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

/**
 * LotteryAdjustServiceImpl
 *
 * <p>调用 DeepSeek，支持两种模式（输出 Schema 相同）：
 * <ul>
 *     <li>调优：tickets 非空 — 逐组调整 + 组内复式 + 最终复式/单式</li>
 *     <li>推荐：tickets 为空 — 按特征报告生成 recommendCount 组 + 组内复式 + 最终复式/单式</li>
 * </ul>
 *
 * @author 刘强
 * @version 2026/08/10 10:30
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
        log.info("开始调用 DeepSeek 调优（单组复式+最终复式+最终单式），候选组数: {}", reqBo.getTickets().size());

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
        if (result.getFinalComplexTicket() == null
            || CollectionUtils.isEmpty(result.getFinalComplexTicket().getRedBalls())
            || CollectionUtils.isEmpty(result.getFinalComplexTicket().getBlueBalls())) {
            throw new IllegalStateException("大模型未返回有效的 finalComplexTicket（最终复式）");
        }
        if (CollectionUtils.isEmpty(result.getFinalSingleTickets())) {
            throw new IllegalStateException("大模型未返回 finalSingleTickets（最终单式）");
        }
        if (result.getFinalSingleTickets().size() != 2) {
            throw new IllegalStateException(
                "finalSingleTickets 必须恰好 2 组，实际: " + result.getFinalSingleTickets().size());
        }
        long missingSingle = result.getFinalSingleTickets().stream()
            .filter(t -> CollectionUtils.isEmpty(t.getRedBalls())
                || t.getRedBalls().size() != 6
                || t.getBlueBall() == null)
            .count();
        if (missingSingle > 0) {
            throw new IllegalStateException(
                "有 " + missingSingle + " 组 finalSingleTickets 未返回有效的 6 红 + 1 蓝");
        }
        if (CollectionUtils.isEmpty(result.getAdjustedTickets())) {
            throw new IllegalStateException("大模型未返回 adjustedTickets（含单组复式）");
        }
        long missingComplex = result.getAdjustedTickets().stream()
            .filter(t -> t.getComplexTicket() == null
                || CollectionUtils.isEmpty(t.getComplexTicket().getRedBalls())
                || CollectionUtils.isEmpty(t.getComplexTicket().getBlueBalls()))
            .count();
        if (missingComplex > 0) {
            throw new IllegalStateException(
                "有 " + missingComplex + " 组未返回有效 complexTicket（单组复式）");
        }
        if (result.getAdjustedTickets().size() != ticketCount) {
            log.warn("adjustedTickets 数量({})与期望组数({})不一致",
                result.getAdjustedTickets().size(), ticketCount);
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
                  "adjustedRedBalls": [int],
                  "adjustedBlueBall": int,
                  "reason": "本组调整说明",
                  "complexTicket": {
                    "name": "本组复式名称",
                    "redBalls": [int],
                    "blueBalls": [int],
                    "totalBets": int,
                    "basis": "本组复式依据"
                  }
                }
              ],
              "finalComplexTicket": {
                "name": "最终复式名称",
                "redBalls": [int],
                "blueBalls": [int],
                "totalBets": int,
                "basis": "最终复式选号依据"
              },
              "finalSingleTickets": [
                {
                  "name": "最终单式名称(如热温延续单式/温冷回冷单式)",
                  "redBalls": [int, int, int, int, int, int],
                  "blueBall": int,
                  "totalBets": 1,
                  "basis": "本组单式针对的形态假设与冷热/分区/连号结构依据"
                },
                {
                  "name": "第二组单式名称",
                  "redBalls": [int, int, int, int, int, int],
                  "blueBall": int,
                  "totalBets": 1,
                  "basis": "本组单式针对的形态假设与冷热/分区/连号结构依据"
                }
              ],
              "conclusion": "综合说明"
            }
            """;
}
