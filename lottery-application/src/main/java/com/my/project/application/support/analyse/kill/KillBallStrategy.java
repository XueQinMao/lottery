package com.my.project.application.support.analyse.kill;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import org.apache.commons.lang3.tuple.Triple;

import java.util.List;
import java.util.Optional;

/**
 * 杀球策略：计算红/蓝候选；是否参与最终汇总由 {@link #enabled()} 决定。
 */
public interface KillBallStrategy {

    /** 是否参与最终硬杀汇总 */
    boolean enabled();

    /** 策略展示名称 */
    String name();

    /**
     * 红球软/硬分流类型（均线 jx / 遗漏 yl / 指数 zs）。
     * 空表示整批进硬杀，不做分流。
     */
    default Optional<String> splitType() {
        return Optional.empty();
    }

    /**
     * 计算红球、篮球杀号候选。
     *
     * @param historyRecords 历史开奖（新→旧）
     * @param nextWinBalls   调试用下期开奖，可空
     */
    KillBallStrategyResult calculate(List<HistoryRecord> historyRecords,
        Triple<List<Integer>, String, Integer> nextWinBalls);
}
