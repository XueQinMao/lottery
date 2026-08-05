package com.my.project.service.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * NextLotteryDateUtils
 *
 * @author 刘强
 * @version 2025/07/29 20:00
 */
public class NextLotteryDateUtils {

    // 双色球开奖日：周二、周四、周日
    private static final List<DayOfWeek> DRAW_DAYS =
        List.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);

    public static LocalDate nextDrawDate() {
        return nextDrawDate(1);
    }

    public static LocalDate prevDrawDate() {
        return Stream.iterate(LocalDate.now().plusDays(-1), d -> d.minusDays(1))
            .filter(d -> DRAW_DAYS.contains(d.getDayOfWeek()))
            .findFirst()
            .orElseThrow();
    }

    public static LocalDate prevDrawDate(int daysToAdd) {
        return Stream.iterate(LocalDate.now().plusDays(daysToAdd), d -> d.plusDays(daysToAdd))
            .filter(d -> DRAW_DAYS.contains(d.getDayOfWeek()))
            .findFirst()
            .orElseThrow();
    }


    /**
     * 获取当前天的上一个或者下一个开奖日期
     * @param daysToAdd
     * @return
     */
    private static LocalDate nextDrawDate(int daysToAdd) {
        return Stream.iterate(LocalDate.now(), d -> d.plusDays(daysToAdd))
            .filter(d -> DRAW_DAYS.contains(d.getDayOfWeek()))
            .findFirst()
            .orElseThrow();
    }


    /**
     * 获取当前日期前10次的开奖日期
     *
     * @return 包含前10次开奖日期的列表
     */
    public static Set<LocalDate> previousDrawDates(int number) {
        return Stream.iterate(LocalDate.now().minusDays(1), d -> d.minusDays(1))
            .filter(d -> DRAW_DAYS.contains(d.getDayOfWeek()))
            .limit(number)
            .collect(Collectors.toCollection(HashSet::new));
    }
}
