package com.my.project.llm.service.impl;

import com.alibaba.fastjson.JSON;
import com.my.project.llm.bo.LotteryAdjustReqBo;
import com.my.project.llm.bo.LotteryAdjustRespBo;
import com.my.project.llm.prompt.LotteryAdjustPrompt;
import com.my.project.llm.service.ILotteryAdjustService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * LotteryAdjustServiceImpl
 *
 * <p>调用 DeepSeek，支持两种模式（输出 Schema 相同）：
 * <ul>
 *     <li>调优：tickets 非空 — 逐组单式调整 + 最终推荐包（3 胆 + 2 单式 + 1 复式）</li>
 *     <li>推荐：tickets 为空 — 按特征报告生成 recommendCount 组单式 + 最终推荐包</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/17
 **/
@Slf4j
@Service
public class LotteryAdjustServiceImpl implements ILotteryAdjustService {

    private static final int DEFAULT_RECOMMEND_COUNT = 3;
    private static final int MAX_RECOMMEND_COUNT = 10;

    private final ChatClient lotteryAdjustChatClient;

    public LotteryAdjustServiceImpl(@Qualifier("lotteryAdjustChatClient") ChatClient lotteryAdjustChatClient) {
        this.lotteryAdjustChatClient = lotteryAdjustChatClient;
    }

    @Override
    public LotteryAdjustRespBo adjust(LotteryAdjustReqBo reqBo) {
        if (reqBo == null) {
            throw new IllegalArgumentException("调优入参不能为空");
        }
        if (reqBo.getAnalysisReportJson() == null || reqBo.getAnalysisReportJson().isBlank()) {
            throw new IllegalArgumentException("特征分析报告不能为空");
        }
        return CollectionUtils.isEmpty(reqBo.getTickets()) ? recommend(reqBo) : adjustExisting(reqBo);
    }

    /**
     * 调优模式：对已有号码组逐一调整。
     */
    private LotteryAdjustRespBo adjustExisting(LotteryAdjustReqBo reqBo) {
        log.info("开始调用 DeepSeek 调优（单组单式+最终推荐包），候选组数: {}", reqBo.getTickets().size());

        String ticketsJson = JSON.toJSONString(reqBo.getTickets());
        LotteryAdjustRespBo result = lotteryAdjustChatClient.prompt()
            .user(u -> u.text(LotteryAdjustPrompt.USER_PROMPT)
                .param("report", reqBo.getAnalysisReportJson())
                .param("tickets", ticketsJson)
                .param("format", FORMAT_HINT))
            .call()
            .entity(LotteryAdjustRespBo.class);

        validateResult(result, reqBo.getTickets().size());
        return result;
    }

    /**
     * 推荐模式：tickets 为空时，按特征报告从零生成 N 组号码。
     */
    private LotteryAdjustRespBo recommend(LotteryAdjustReqBo reqBo) {
        int count = resolveRecommendCount(reqBo.getCount());
        log.info("开始调用 DeepSeek 推荐（无候选号码，按特征报告生成），组数: {}", count);

        LotteryAdjustRespBo result = lotteryAdjustChatClient.prompt()
            .user(u -> u.text(LotteryAdjustPrompt.RECOMMEND_PROMPT)
                .param("report", reqBo.getAnalysisReportJson())
                .param("count", String.valueOf(count))
                .param("format", FORMAT_HINT))
            .call()
            .entity(LotteryAdjustRespBo.class);

        validateResult(result, count);
        return result;
    }

    private int resolveRecommendCount(Integer recommendCount) {
        int count = recommendCount == null ? DEFAULT_RECOMMEND_COUNT : recommendCount;
        if (count < 1) {
            count = DEFAULT_RECOMMEND_COUNT;
        }
        return Math.min(count, MAX_RECOMMEND_COUNT);
    }

    private void validateResult(LotteryAdjustRespBo result, int ticketCount) {
        if (result == null) {
            throw new IllegalStateException("大模型返回为空");
        }
        if (CollectionUtils.isEmpty(result.getAdjustedTickets())) {
            throw new IllegalStateException("大模型未返回 adjustedTickets");
        }
        long missingSingle = result.getAdjustedTickets().stream()
            .filter(t -> CollectionUtils.isEmpty(t.getAdjustedRedBalls())
                || t.getAdjustedRedBalls().size() != 6
                || t.getAdjustedBlueBall() == null)
            .count();
        if (missingSingle > 0) {
            throw new IllegalStateException(
                "有 " + missingSingle + " 组 adjustedTickets 未返回有效的 6 红 + 1 蓝");
        }
        if (result.getAdjustedTickets().size() != ticketCount) {
            log.warn("adjustedTickets 数量({})与期望组数({})不一致",
                result.getAdjustedTickets().size(), ticketCount);
        }

        LotteryAdjustRespBo.FinalRecommendation finalRec = result.getFinalRecommendation();
        if (finalRec == null) {
            throw new IllegalStateException("大模型未返回 finalRecommendation（最终推荐包）");
        }
        if (CollectionUtils.isEmpty(finalRec.getDanBalls()) || finalRec.getDanBalls().size() != 3) {
            throw new IllegalStateException(
                "finalRecommendation.danBalls 必须恰好 3 个，实际: "
                    + (finalRec.getDanBalls() == null ? 0 : finalRec.getDanBalls().size()));
        }
        if (CollectionUtils.isEmpty(finalRec.getSingleTickets())) {
            throw new IllegalStateException("大模型未返回 finalRecommendation.singleTickets（最终单式）");
        }
        if (finalRec.getSingleTickets().size() != 2) {
            throw new IllegalStateException(
                "finalRecommendation.singleTickets 必须恰好 2 组，实际: "
                    + finalRec.getSingleTickets().size());
        }
        long invalidFinalSingle = finalRec.getSingleTickets().stream()
            .filter(t -> CollectionUtils.isEmpty(t.getRedBalls())
                || t.getRedBalls().size() != 6
                || t.getBlueBall() == null)
            .count();
        if (invalidFinalSingle > 0) {
            throw new IllegalStateException(
                "有 " + invalidFinalSingle + " 组 finalRecommendation.singleTickets 未返回有效的 6 红 + 1 蓝");
        }
        if (finalRec.getComplexTicket() == null
            || CollectionUtils.isEmpty(finalRec.getComplexTicket().getRedBalls())
            || CollectionUtils.isEmpty(finalRec.getComplexTicket().getBlueBalls())) {
            throw new IllegalStateException("大模型未返回有效的 finalRecommendation.complexTicket（最终复式）");
        }
        int redSize = finalRec.getComplexTicket().getRedBalls().size();
        int blueSize = finalRec.getComplexTicket().getBlueBalls().size();
        if (redSize < 7 || redSize > 10 || blueSize < 2 || blueSize > 5) {
            throw new IllegalStateException(
                "finalRecommendation.complexTicket 红球须 7-10、蓝球须 2-5，实际红="
                    + redSize + " 蓝=" + blueSize);
        }

        Set<Integer> danSet = new HashSet<>(finalRec.getDanBalls());
        for (int i = 0; i < finalRec.getSingleTickets().size(); i++) {
            List<Integer> reds = finalRec.getSingleTickets().get(i).getRedBalls();
            if (!new HashSet<>(reds).containsAll(danSet)) {
                throw new IllegalStateException(
                    "finalRecommendation.singleTickets[" + i + "] 未包含全部 3 个胆码");
            }
        }
        if (!new HashSet<>(finalRec.getComplexTicket().getRedBalls()).containsAll(danSet)) {
            throw new IllegalStateException(
                "finalRecommendation.complexTicket 未包含全部 3 个胆码");
        }
    }

    private static final String FORMAT_HINT = """
            {
              "adjustedTickets": [
                {
                  "id": "组标识(可选)",
                  "originalRedBalls": [int],
                  "originalBlueBall": int,
                  "redReplacements": [ { "from": int, "to": int, "basis": "依据" } ],
                  "blueReplacement": { "from": int, "to": int, "basis": "依据" },
                  "adjustedRedBalls": [int, int, int, int, int, int],
                  "adjustedBlueBall": int,
                  "reason": "本组调整说明"
                }
              ],
              "finalRecommendation": {
                "danBalls": [int, int, int],
                "danBasis": "三胆选号依据",
                "singleTickets": [
                  {
                    "name": "最终单式名称(如热温延续单式)",
                    "redBalls": [int, int, int, int, int, int],
                    "blueBall": int,
                    "totalBets": 1,
                    "basis": "本组单式针对的形态假设与冷热/分区/连号结构依据"
                  },
                  {
                    "name": "第二组单式名称(如温冷回冷单式)",
                    "redBalls": [int, int, int, int, int, int],
                    "blueBall": int,
                    "totalBets": 1,
                    "basis": "本组单式针对的形态假设与冷热/分区/连号结构依据"
                  }
                ],
                "complexTicket": {
                  "name": "最终复式名称",
                  "redBalls": [int],
                  "blueBalls": [int],
                  "totalBets": int,
                  "basis": "最终复式选号依据"
                }
              },
              "conclusion": "综合说明"
            }
            """;
}
