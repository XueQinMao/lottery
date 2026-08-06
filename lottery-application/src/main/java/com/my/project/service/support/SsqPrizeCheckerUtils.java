package com.my.project.service.support;

import com.my.project.service.enums.PrizeLevelEnum;

import java.util.List;

/**
 * SsqPrizeCheckerUtils
 *
 * @author 刘强
 * @version 2025/11/03 11:22
 **/
public class SsqPrizeCheckerUtils {

    /**
     * 判断中奖等级
     *
     * @param winningRedBalls 开奖红球（6个）
     * @param winningBlueBall 开奖蓝球（1个）
     * @param userRedBalls    用户投注红球（6个）
     * @param userBlueBall    用户投注蓝球（1个）
     * @return 奖级
     */
    public static PrizeLevelEnum checkPrize(List<Integer> winningRedBalls, int winningBlueBall,
        List<Integer> userRedBalls, List<Integer> userBlueBalls) {
        // 统计红球命中数
        long redHitCount = userRedBalls.stream().filter(winningRedBalls::contains).count();
        // 蓝球命中
        boolean blueHit = userBlueBalls.contains(winningBlueBall);
        // 判断奖级
        if (redHitCount == 6 && blueHit) {
            return PrizeLevelEnum.FIRST;
        } else if (redHitCount == 6) {
            return PrizeLevelEnum.SECOND;
        } else if (redHitCount == 5 && blueHit) {
            return PrizeLevelEnum.THIRD;
        } else if ((redHitCount == 5 && !blueHit) || (redHitCount == 4 && blueHit)) {
            return PrizeLevelEnum.FOURTH;
        } else if ((redHitCount == 4 && !blueHit) || (redHitCount == 3 && blueHit)) {
            return PrizeLevelEnum.FIFTH;
        } else if (blueHit) {
            return PrizeLevelEnum.SIXTH;
        } else {
            return PrizeLevelEnum.NO_PRIZE;
        }
    }
}
