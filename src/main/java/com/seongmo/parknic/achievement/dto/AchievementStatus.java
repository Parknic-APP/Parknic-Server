package com.seongmo.parknic.achievement.dto;

import java.time.Instant;

public record AchievementStatus(
        String code,
        String name,
        String label,
        Instant achievedAt,
        int progress,
        int target
) {
}
