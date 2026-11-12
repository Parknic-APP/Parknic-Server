package com.seongmo.parknic.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class MutableClockTest {

    @Test
    void advance로_시각을_앞으로_옮긴다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T03:00:00Z"));

        clock.advance(Duration.ofMinutes(20));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-06T03:20:00Z"));
    }

    @Test
    void set으로_시각을_직접_지정한다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-06T03:00:00Z"));

        clock.set(Instant.parse("2026-11-07T00:00:00Z"));

        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-11-07T00:00:00Z"));
    }
}
