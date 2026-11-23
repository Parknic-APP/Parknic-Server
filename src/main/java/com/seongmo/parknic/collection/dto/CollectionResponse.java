package com.seongmo.parknic.collection.dto;

import com.seongmo.parknic.park.entity.ParkType;

import java.util.List;

public record CollectionResponse(
        int totalBadges,
        int visitedParks,
        List<ParkTypeCount> types,
        List<AchievementItem> achievements
) {

    public record ParkTypeCount(ParkType parkType, int count) { }

    public record AchievementItem(
            String code,
            String name,
            String label,
            String achievedAt,
            int progress,
            int target
    ) { }
}
