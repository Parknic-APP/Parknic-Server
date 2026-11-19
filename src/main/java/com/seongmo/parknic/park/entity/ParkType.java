package com.seongmo.parknic.park.entity;

import java.util.Optional;

public enum ParkType {
    NEIGHBORHOOD,
    CHILDREN,
    SMALL,
    SPORTS,
    WATERFRONT,
    CULTURE,
    HISTORY;

    public static Optional<ParkType> fromKorean(String name) {
        return Optional.ofNullable(switch (name.trim()) {
            case "근린공원" -> NEIGHBORHOOD;
            case "어린이공원" -> CHILDREN;
            case "소공원" -> SMALL;
            case "체육공원" -> SPORTS;
            case "수변공원" -> WATERFRONT;
            case "문화공원" -> CULTURE;
            case "역사공원" -> HISTORY;
            default -> null;
        });
    }
}
