package com.my.project.application.feature.support;

import com.my.project.infrastructure.persistence.entity.HistoryRecord;
import com.my.project.infrastructure.persistence.repository.HistoryRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
public class ForecastTargetPeriod {
    private static final EnumSet<DayOfWeek> DRAW_DAYS = EnumSet.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY);
    private final HistoryRecordRepository history;

    public String after(HistoryRecord cutoff) {
        String sourcePeriod = validate(cutoff.getPeriod());
        LocalDate drawDate = cutoff.getOpenDate();
        Assert.notNull(drawDate, "截止期缺少开奖日期");
        return history.findNextPeriod(sourcePeriod).orElseGet(() -> expectedAfter(sourcePeriod, drawDate));
    }

    static String expectedAfter(String sourcePeriod, LocalDate drawDate) {
        LocalDate nextDate = drawDate.plusDays(1);
        while (!DRAW_DAYS.contains(nextDate.getDayOfWeek())) {
            nextDate = nextDate.plusDays(1);
        }
        if (nextDate.getYear() > drawDate.getYear()) return "%04d001".formatted(nextDate.getYear());
        return Integer.toString(Integer.parseInt(sourcePeriod) + 1);
    }

    public static String validate(String period) {
        Assert.isTrue(period != null && period.matches("[1-9][0-9]{6}") && !period.endsWith("000"), "期号格式应为7位数字");
        return period;
    }
}
