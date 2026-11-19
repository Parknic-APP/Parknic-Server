package com.seongmo.parknic.park.dto;

import com.seongmo.parknic.park.entity.ParkType;

public record ParkSummaryResponse(
        Long id,
        String name,
        ParkType parkType,
        double latitude,
        double longitude,
        int radiusM,
        int distanceM,
        ParkBadgeStatus myStatus,
        int myBadgeCount
) {
}
