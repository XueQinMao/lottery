package com.my.project.application;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
/** 只保留已整合的重号和最大邻差两项规则。 */
class XiaohongshuKillRulesVerificationTest {
    @Test void retainedRulesMatchUserReport() {
        var context = new ToutiaoKillRules.Context("2026119", List.of(
                ToutiaoKillRulesVerificationTest.draw("2026118", 14, 5, 21, 22, 23, 28, 31),
                ToutiaoKillRulesVerificationTest.draw("2026117", 4, 4, 11, 14, 22, 26, 27)));
        var rules = XiaohongshuKillRules.catalog();
        assertEquals(List.of("X-R04", "X-R06"), rules.stream().map(ToutiaoKillRules.Rule::id).toList());
        assertEquals(List.of(22), rules.getFirst().kills(context));
        assertEquals(List.of(15), rules.getLast().kills(context));
    }
    @Test void consecutiveNumbersDoNotMapZeroToAnotherBall() {
        var context = new ToutiaoKillRules.Context("2026119", List.of(
                ToutiaoKillRulesVerificationTest.draw("2026118", 16, 1, 2, 3, 4, 5, 6)));
        assertEquals(List.of(), XiaohongshuKillRules.catalog().getLast().kills(context));
    }
}
