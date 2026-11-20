package com.seongmo.parknic.park.dto;

public record ParkMyBadge(ParkBadgeStatus status, int count) {

    public static ParkMyBadge none() {
        return new ParkMyBadge(ParkBadgeStatus.NONE, 0);
    }
}
