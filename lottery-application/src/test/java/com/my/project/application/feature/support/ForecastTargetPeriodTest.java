package com.my.project.application.feature.support;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ForecastTargetPeriodTest {
    @Test void incrementsWithinTheCalendarYearAndResetsTheNextYear() {
        assertEquals("2026133", ForecastTargetPeriod.expectedAfter("2026132", LocalDate.of(2026, 11, 17)));
        assertEquals("2027001", ForecastTargetPeriod.expectedAfter("2026152", LocalDate.of(2026, 12, 31)));
        assertEquals("2026152", ForecastTargetPeriod.expectedAfter("2026151", LocalDate.of(2026, 12, 29)));
    }
}
