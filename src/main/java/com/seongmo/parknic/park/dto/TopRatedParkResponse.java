package com.seongmo.parknic.park.dto;

import com.seongmo.parknic.park.entity.ParkType;

public record TopRatedParkResponse(
        Long id,
        String name,
        ParkType parkType,
        double latitude,
        double longitude,
        int radiusM,
        int distanceM,
        double averageRating,
        int reviewCount,
        ParkBadgeStatus myStatus,
        int myBadgeCount
) {
}
