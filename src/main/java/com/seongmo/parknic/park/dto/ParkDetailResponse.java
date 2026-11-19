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

    public static ParkDetailResponse from(Park park) {
        return new ParkDetailResponse(
                park.getId(),
                park.getName(),
                park.getParkType(),
                park.getAddress(),
                park.getLatitude(),
                park.getLongitude(),
                park.getRadiusM(),
                // 리뷰, 배지가 생기기 전까지는 고정값
                null,
                0,
                ParkBadgeStatus.NONE,
                0
        );
    }
}
