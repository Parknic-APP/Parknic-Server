package com.seongmo.parknic.user.dto;

public record UserStatsResponse(
        int totalBadges,
        int visitedParks,
        int currentStreak,
        int bestStreak,
        int podiumParks
) {
}
