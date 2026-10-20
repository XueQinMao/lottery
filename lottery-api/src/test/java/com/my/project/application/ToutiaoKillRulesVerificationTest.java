package com.my.project.application;
import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** 只验证已整合的头条规则，计算调用生产共享目录。 */
class ToutiaoKillRulesVerificationTest {
    static HistoryRecord draw(String period, int blue, int... r) {
        return HistoryRecord.builder().period(period).special(blue).num1(r[0]).num2(r[1])
                .num3(r[2]).num4(r[3]).num5(r[4]).num6(r[5]).build();
    }
    @Test void retainedArithmeticMatchesTheUserReport() {
        var context = new ToutiaoKillRules.Context("2026119", List.of(
                draw("2026118", 14, 5, 21, 22, 23, 28, 31), draw("2026117", 4, 4, 11, 14, 22, 26, 27)));
        Map<String, List<Integer>> expected = Map.of("R02", List.of(28), "R04", List.of(9),
                "R09", List.of(10), "R11", List.of(19), "R14", List.of(11),
                "B01", List.of(3), "B02", List.of(15, 16), "B03", List.of(10),
                "B05", List.of(1, 11), "B08", List.of(6, 16));
        var rules = ToutiaoKillRules.catalog();
        assertEquals(expected.size(), rules.size());
        rules.forEach(rule -> assertEquals(expected.get(rule.id()), rule.kills(context), rule.id()));
    }
    @Test void zeroFilteringWrapAndSameBlueDifference() {
        var context = new ToutiaoKillRules.Context("2026119", List.of(
                draw("2026118", 16, 1, 2, 3, 4, 5, 6), draw("2026117", 16, 7, 8, 9, 10, 11, 12)));
        Map<String, List<Integer>> expected = Map.of("R04", List.of(), "B02", List.of(1, 2),
                "B03", List.of(), "B05", List.of(9));
        ToutiaoKillRules.catalog().stream().filter(r -> expected.containsKey(r.id()))
                .forEach(r -> assertEquals(expected.get(r.id()), r.kills(context), r.id()));
    }
}
