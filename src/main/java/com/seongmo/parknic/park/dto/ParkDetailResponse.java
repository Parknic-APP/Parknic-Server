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

    public static ParkDetailResponse of(Park park, ParkMyBadge myBadge) {
        return new ParkDetailResponse(
                park.getId(),
                park.getName(),
                park.getParkType(),
                park.getAddress(),
                park.getLatitude(),
                park.getLongitude(),
                park.getRadiusM(),
                // 리뷰가 생기기 전까지는 averageRating, reviewCount가 고정값
                null,
                0,
                myBadge.status(),
                myBadge.count()
        );
    }
}
