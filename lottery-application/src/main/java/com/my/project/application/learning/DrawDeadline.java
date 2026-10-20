package com.my.project.application.learning;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

public final class DrawDeadline {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalTime DRAW_TIME = LocalTime.of(21, 15);

    private DrawDeadline() { }

    public static Instant at(LocalDate drawDate) {
        return drawDate.atTime(DRAW_TIME).atZone(ZONE).toInstant();
    }
}
