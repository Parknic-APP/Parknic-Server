package com.seongmo.parknic.checkin.dto;

import com.seongmo.parknic.park.entity.ParkType;

import java.time.OffsetDateTime;
import java.util.List;

public record CompleteCheckinResponse(
        BadgeInfo badge,
        List<AchievementInfo> newAchievements,
        WeeklyRankingInfo weeklyRanking,
        boolean canWriteReview
) {

    public record BadgeInfo(Long parkId, String parkName, ParkType parkType, OffsetDateTime earnedAt) {}

    public record AchievementInfo(String code, String name) {}

    public record WeeklyRankingInfo(int rank, int badgeCount, int rankChange, int badgesToPodium) {}
}
