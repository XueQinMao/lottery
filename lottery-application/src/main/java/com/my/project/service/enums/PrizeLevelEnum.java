package com.my.project.service.enums;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * PrizeLevelEnum
 *
 * @author 刘强
 * @version 2026/01/06 15:00
 **/
public enum PrizeLevelEnum {

    FIRST(true, 1), SECOND(true,2), THIRD(true,3), FOURTH(true,4), FIFTH(true,5), SIXTH(true, 6), NO_PRIZE(
        false, 0);

    private boolean isHit;

    private int level;

    PrizeLevelEnum(boolean isHit, int level) {
        this.isHit = isHit;
        this.level = level;
    }

    public boolean isHit() {
        return isHit;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public void setHit(boolean hit) {
        isHit = hit;
    }

    public static List<PrizeLevelEnum> getHitPrizeLevels() {
        return Arrays.stream(PrizeLevelEnum.values()).filter(PrizeLevelEnum::isHit).toList();
    }

    public static PrizeLevelEnum getPrizeLevel(int level) {
        return Arrays.stream(PrizeLevelEnum.values()).filter(val -> val.getLevel() == level).findFirst().orElse(null);
    }

    public static PrizeLevelEnum checkPrize(List<Integer> winningRedBalls, int winningBlueBall,
        List<Integer> userRedBalls, List<Integer> userBlueBalls) {
        // 统计红球命中数
        long redHitCount = userRedBalls.stream().filter(winningRedBalls::contains).count();
        // 蓝球命中
        boolean blueHit = userBlueBalls.contains(winningBlueBall);
        // 默认升序
        return RULES.stream().filter(r -> r.redCount == redHitCount && r.blueHit == blueHit).map(Rule::prize)
            .min(Comparator.comparing(PrizeLevelEnum::getLevel)).orElse(NO_PRIZE);
    }

    private static final List<Rule> RULES =
        List.of(new Rule(6, true, FIRST), new Rule(6, false, SECOND), new Rule(5, true, THIRD),
            new Rule(5, false, FOURTH), new Rule(4, true, FOURTH), new Rule(4, false, FIFTH), new Rule(3, true, FIFTH),
            new Rule(0, true, SIXTH));

    record Rule(int redCount, boolean blueHit, PrizeLevelEnum prize) {}
}
