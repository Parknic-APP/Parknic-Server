package com.seongmo.parknic.user.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 6);

    @Test
    void 배지를_받은_날이_없으면_현재도_최고도_0이다() {
        assertThat(StreakCalculator.current(List.of(), TODAY)).isZero();
        assertThat(StreakCalculator.best(List.of())).isZero();
    }

    @Test
    void 오늘만_받았으면_1이다() {
        List<LocalDate> dates = days("2026-11-06");

        assertThat(StreakCalculator.current(dates, TODAY)).isEqualTo(1);
        assertThat(StreakCalculator.best(dates)).isEqualTo(1);
    }

    @Test
    void 오늘은_아직_안_받았어도_어제까지_이어졌으면_현재_연속으로_센다() {
        List<LocalDate> dates = days("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05");

        assertThat(StreakCalculator.current(dates, TODAY)).isEqualTo(5);
        assertThat(StreakCalculator.best(dates)).isEqualTo(5);
    }

    @Test
    void 마지막이_이틀_전이면_현재_연속은_0이고_최고_기록은_남는다() {
        List<LocalDate> dates = days("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04");

        assertThat(StreakCalculator.current(dates, TODAY)).isZero();
        assertThat(StreakCalculator.best(dates)).isEqualTo(4);
    }

    @Test
    void 중간에_끊기면_끊긴_뒤의_구간만_현재_연속이다() {
        List<LocalDate> dates = days("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-05", "2026-11-06");

        assertThat(StreakCalculator.current(dates, TODAY)).isEqualTo(2);
        assertThat(StreakCalculator.best(dates)).isEqualTo(3);
    }

    @Test
    void 과거에_더_긴_구간이_있으면_최고_기록은_그_구간이다() {
        List<LocalDate> dates = days(
                "2026-10-01", "2026-10-02", "2026-10-03", "2026-10-04", "2026-10-05", "2026-10-06", "2026-10-07",
                "2026-11-05", "2026-11-06");

        assertThat(StreakCalculator.current(dates, TODAY)).isEqualTo(2);
        assertThat(StreakCalculator.best(dates)).isEqualTo(7);
    }

    @Test
    void 달이_바뀌어도_연속이다() {
        List<LocalDate> dates = days("2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02");

        assertThat(StreakCalculator.best(dates)).isEqualTo(4);
        assertThat(StreakCalculator.current(dates, LocalDate.of(2026, 11, 3))).isEqualTo(4);
    }

    private static List<LocalDate> days(String... isoDates) {
        return java.util.Arrays.stream(isoDates).map(LocalDate::parse).toList();
    }
}
