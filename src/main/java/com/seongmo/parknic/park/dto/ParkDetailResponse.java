package com.seongmo.parknic.park.dto;

import com.seongmo.parknic.park.entity.Park;
import com.seongmo.parknic.park.entity.ParkType;

public record ParkDetailResponse(
        Long id,
        String name,
        ParkType parkType,
        String address,
        double latitude,
        double longitude,
        int radiusM,
        Double averageRating,
        int reviewCount,
        ParkBadgeStatus myStatus,
        int myBadgeCount
) {

    public static ParkDetailResponse of(Park park, ParkMyBadge myBadge, ParkReviewStats stats) {
        return new ParkDetailResponse(
                park.getId(),
                park.getName(),
                park.getParkType(),
                park.getAddress(),
                park.getLatitude(),
                park.getLongitude(),
                park.getRadiusM(),
                stats.averageRating(),
                stats.reviewCount(),
                myBadge.status(),
                myBadge.count()
        );
    }
}
