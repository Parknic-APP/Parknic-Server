package com.seongmo.parknic.common.time;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

public class Kst {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    public static final DateTimeFormatter ISO_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX").withZone(ZONE);

    private Kst() {}

    // 지금 시각의 KST 날짜
    public static LocalDate today(Clock clock) {
        return dateOf(clock.instant());
    }

    // 어떤 시점이 KST 기준 며칠인지 반환
    public static LocalDate dateOf(Instant instant) {
        return instant.atZone(ZONE).toLocalDate();
    }

    // KST 그 날짜의 00:00에 해당하는 시점 반환
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    // 응답용 문자열 : 2026-11-12T15:32:10+09:00 (초 단위)
    public static String format(Instant instant) {
        return ISO_SECONDS.format(instant.truncatedTo(ChronoUnit.SECONDS));
    }

    // 그 날짜가 속한 주의 월요일
    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
