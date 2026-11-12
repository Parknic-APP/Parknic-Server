package com.seongmo.parknic.common.time;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class KstTest {

    @Test
    void 자정_1초_전은_전날이고_자정은_다음날이다() {
        // 2026-11-08 23:59:59 KST = 2026-11-08 14:59:59 UTC
        assertThat(Kst.dateOf(Instant.parse("2026-11-08T14:59:59Z")))
                .isEqualTo(LocalDate.of(2026, 11, 8));

        // 2026-11-09 00:00:00 KST = 2026-11-09 15:00:00 UTC
        assertThat(Kst.dateOf(Instant.parse("2026-11-08T15:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void today는_Clock의_존과_상관없이_KST_날짜다() {
        Instant instant = Instant.parse("2026-11-08T20:00:00Z"); // KST 11-9 05:00
        Clock utc = Clock.fixed(instant, ZoneOffset.UTC);

        assertThat(Kst.today(utc)).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    @Test
    void startOfDay는_KST_00시에_해당하는_시점이다() {
        assertThat(Kst.startOfDay(LocalDate.of(2026, 11, 9)))
                .isEqualTo(Instant.parse("2026-11-08T15:00:00Z"));
    }

    @Test
    void format은_초_단위_플러스_9시간_문자열이다() {
        assertThat(Kst.format(Instant.parse("2026-11-04T06:32:10Z")))
                .isEqualTo("2026-11-04T15:32:10+09:00");
    }

    @Test
    void format은_소수점_이하를_버린다() {
        assertThat(Kst.format(Instant.parse("2026-11-04T06:32:10.987654Z")))
                .isEqualTo("2026-11-04T15:32:10+09:00");
    }
}