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
 * <p>调用 DeepSeek：每组输出单式调整 + 组内复式，并额外输出一组最终可购买复式
 * （{@link LotteryAdjustRespBo#getFinalComplexTicket()}）
 * 与两组最终可购买单式（{@link LotteryAdjustRespBo#getFinalSingleTickets()}）。
 *
 * @author 刘强
 * @version 2026/07/22 11:50
 **/
@Slf4j
@Service
public class LotteryAdjustServiceImpl implements ILotteryAdjustService {

    private final ChatClient lotteryAdjustChatClient;

    public LotteryAdjustServiceImpl(@Qualifier("lotteryAdjustChatClient") ChatClient lotteryAdjustChatClient) {
        this.lotteryAdjustChatClient = lotteryAdjustChatClient;
    }

    @Override
    public LotteryAdjustRespBo adjust(LotteryAdjustReqBo reqBo) {
        if (reqBo == null || reqBo.getTickets() == null || reqBo.getTickets().isEmpty()) {
            throw new IllegalArgumentException("待调整的预测号码组不能为空");
        }
        if (reqBo.getAnalysisReportJson() == null || reqBo.getAnalysisReportJson().isBlank()) {
            throw new IllegalArgumentException("特征分析报告不能为空");
        }
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

        log.info("DeepSeek 调优完成: groups={}, finalRed={}, finalBlue={}, totalBets={}, singleTickets={}",
            CollectionUtils.size(result.getAdjustedTickets()),
            result.getFinalComplexTicket().getRedBalls(),
            result.getFinalComplexTicket().getBlueBalls(),
            result.getFinalComplexTicket().getTotalBets(),
            CollectionUtils.size(result.getFinalSingleTickets()));
        return result;
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
            log.warn("adjustedTickets 数量({})与入参组数({})不一致",
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
