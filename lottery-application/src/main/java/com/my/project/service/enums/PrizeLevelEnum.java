package com.my.project.service.enums;

import java.util.Arrays;
import java.util.List;

/**
 * PrizeLevelEnum
 *
 * @author 刘强
 * @version 2026/01/06 15:00
 **/
public enum PrizeLevelEnum {

    FIRST(true, 1), SECOND(true,2), THIRD(true,3), FOURTH(true,4), FIFTH(false,5), SIXTH(false, 6), NO_PRIZE(
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
}
