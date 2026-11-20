package com.seongmo.parknic.park.dto;

public enum ParkBadgeStatus {
    NONE,
    EARNED,
    EARNED_TODAY;

    public static ParkBadgeStatus of(long badgeCount, boolean earnedToday) {
        if (badgeCount == 0) {
            return NONE;
        }
        return earnedToday ? EARNED_TODAY : EARNED;
    }
}
