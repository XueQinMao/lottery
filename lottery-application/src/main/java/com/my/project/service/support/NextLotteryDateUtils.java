package com.my.project.service.support;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NextLotteryDateUtils
 *
 * @author 刘强
 * @version 2025/07/29 20:00
 */
public class NextLotteryDateUtils {

    // 双色球开奖日：周二、周四、周日
    private static final List<DayOfWeek> DRAW_DAYS =
        Arrays.asList(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);

    public static LocalDate nextDrawDate() {
        return nextDrawDate(1);
    }

    public static LocalDate prevDrawDate() {
        LocalDate date = LocalDate.now().plusDays(-1);
        while (true) {
            if (DRAW_DAYS.contains(date.getDayOfWeek())) {
                return date;
            }
            date = date.plusDays(-1);
        }
    }

    public static LocalDate prevDrawDate(int daysToAdd) {
        LocalDate date = LocalDate.now().plusDays(daysToAdd);
        while (true) {
            if (DRAW_DAYS.contains(date.getDayOfWeek())) {
                return date;
            }
            date = date.plusDays(daysToAdd);
        }
    }


    /**
     * 获取当前天的上一个或者下一个开奖日期
     * @param daysToAdd
     * @return
     */
    private static LocalDate nextDrawDate(int daysToAdd) {
        LocalDate date = LocalDate.now();
        while (true) {
            if (DRAW_DAYS.contains(date.getDayOfWeek())) {
                return date;
            }
            date = date.plusDays(daysToAdd);
        }
    }


    /**
     * 获取当前日期前10次的开奖日期
     *
     * @return 包含前10次开奖日期的列表
     */
    public static Set<LocalDate> previousDrawDates(int number) {
        Set<LocalDate> drawDates = new HashSet<>();
        LocalDate date = LocalDate.now().minusDays(1); // 从昨天开始往前查找

        while (drawDates.size() < number) {
            if (DRAW_DAYS.contains(date.getDayOfWeek())) {
                drawDates.add(date);
            }
            date = date.minusDays(1);
        }
        return drawDates;
    }
}
