package com.seongmo.parknic.user.service;

import java.time.LocalDate;
import java.util.List;

public final class StreakCalculator {

    private StreakCalculator() {
    }

    // 지금까지 가장 길었던 연속 일수. dates는 중복 없는 오름차순
    public static int best(List<LocalDate> dates) {
        int best = 0;
        int run = 0;
        LocalDate prev = null;
        for (LocalDate date : dates) {
            run = (prev != null && prev.plusDays(1).equals(date)) ? run + 1 : 1;
            best = Math.max(best, run);
            prev = date;
        }
        return best;
    }

    // 오늘 또는 어제까지 이어진 가장 최근 연속 일수. 어제도 오늘도 아니면 0
    public static int current(List<LocalDate> dates, LocalDate today) {
        if (dates.isEmpty()) {
            return 0;
        }
        LocalDate last = dates.get(dates.size() - 1);
        if (last.isBefore(today.minusDays(1))) {
            return 0;
        }

        int run = 1;
        for (int i = dates.size() - 1; i > 0; i--) {
            if (!dates.get(i - 1).plusDays(1).equals(dates.get(i))) {
                break;
            }
            run++;
        }
        return run;
    }
}
