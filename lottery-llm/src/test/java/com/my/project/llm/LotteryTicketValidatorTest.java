package com.my.project.llm;

import com.alibaba.fastjson2.JSON;
import com.my.project.llm.bo.*;
import com.my.project.llm.support.LotteryTicketValidator;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LotteryTicketValidatorTest {
    @Test
    void acceptsValidTicketsAndRejectsNumbersBetsAndKillViolations() {
        var req = request();
        assertDoesNotThrow(() -> LotteryTicketValidator.validate(result(), req, 2));
        var bets = result();
        bets.getFinalRecommendation().getComplexTicket().setTotalBets(1);
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(bets, req, 2));
        var duplicate = result();
        duplicate.getAdjustedTickets().getFirst().setAdjustedRedBalls(List.of(6, 6, 14, 20, 25, 30));
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(duplicate, req, 2));
        var beyond = result();
        beyond.getAdjustedTickets().getFirst().setAdjustedBlueBall(17);
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(beyond, req, 2));
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(result(), req, 3));
        var killedBlue = result();
        killedBlue.getFinalRecommendation().getSingleTickets().getFirst().setBlueBall(16);
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(killedBlue, req, 2));
        var killedRed = result();
        killedRed.getAdjustedTickets().getLast().setAdjustedRedBalls(List.of(6, 11, 14, 20, 25, 30));
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(killedRed, req, 2));
        var fourTail = result();
        fourTail.getFinalRecommendation().getComplexTicket().setRedBalls(List.of(3, 6, 13, 14, 20, 23, 33));
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(fourTail, req, 2));
    }

    @Test
    void checksSingleStructureAndActualAdjustmentDifferences() {
        var req = request();
        var out = result();
        var first = LotteryAdjustReqBo.PredictTicket.builder().id("T1")
            .redBalls(List.of(6, 10, 14, 20, 25, 30)).blueBall(8).build();
        var second = LotteryAdjustReqBo.PredictTicket.builder().id("T2")
            .redBalls(List.of(8, 12, 16, 19, 24, 29)).blueBall(9).build();
        req.setTickets(List.of(first, second));
        for (int index = 0; index < 2; index++) {
            var original = req.getTickets().get(index);
            var ticket = out.getAdjustedTickets().get(index);
            ticket.setId(original.getId());
            ticket.setOriginalRedBalls(original.getRedBalls());
            ticket.setOriginalBlueBall(original.getBlueBall());
        }
        var replace = new LotteryAdjustRespBo.RedReplacement();
        replace.setFrom(10); replace.setTo(11);
        out.getAdjustedTickets().getFirst().setRedReplacements(List.of(replace));
        assertDoesNotThrow(() -> LotteryTicketValidator.validate(out, req, 2));
        replace.setTo(12);
        assertThrows(IllegalStateException.class, () -> LotteryTicketValidator.validate(out, req, 2));
    }

    private LotteryAdjustReqBo request() {
        var kill = KillNumberResultBo.builder()
            .hardKillRed(List.of(KillNumberResultBo.KillItemBo.builder().ball(11).build()))
            .hardKillBlue(List.of(KillNumberResultBo.KillItemBo.builder().ball(16).build())).build();
        return LotteryAdjustReqBo.builder().analysisReportJson(JSON.toJSONString(
            LotteryAnalysisRespBo.builder().killNumbers(kill).featureAnalysis(List.of()).build())).build();
    }

    private LotteryAdjustRespBo result() {
        List<Integer> first = List.of(6, 11, 14, 20, 25, 30);
        List<Integer> second = List.of(8, 12, 16, 19, 24, 29);
        LotteryAdjustRespBo out = new LotteryAdjustRespBo();
        List<LotteryAdjustRespBo.AdjustedTicket> adjusted = new ArrayList<>();
        List<LotteryAdjustRespBo.SingleTicket> singles = new ArrayList<>();
        for (int index = 0; index < 2; index++) {
            List<Integer> reds = index == 0 ? first : second;
            int blue = 8 + index;
            var ticket = new LotteryAdjustRespBo.AdjustedTicket();
            ticket.setAdjustedRedBalls(reds); ticket.setAdjustedBlueBall(blue); ticket.setRedReplacements(List.of());
            adjusted.add(ticket);
            var single = new LotteryAdjustRespBo.SingleTicket();
            single.setRedBalls(reds); single.setBlueBall(blue); single.setTotalBets(1); singles.add(single);
        }
        var complex = new LotteryAdjustRespBo.ComplexTicket();
        complex.setRedBalls(List.of(6, 11, 14, 18, 20, 25, 30));
        complex.setBlueBalls(List.of(8, 9)); complex.setTotalBets(14);
        var pack = new LotteryAdjustRespBo.FinalRecommendation();
        pack.setSingleTickets(singles); pack.setComplexTicket(complex);
        out.setAdjustedTickets(adjusted); out.setFinalRecommendation(pack);
        return out;
    }
}
