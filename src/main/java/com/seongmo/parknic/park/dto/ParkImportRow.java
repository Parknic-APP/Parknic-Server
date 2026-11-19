package com.seongmo.parknic.park.dto;

import com.seongmo.parknic.park.entity.ParkType;

public record ParkImportRow(
        String manageNo,
        String name,
        ParkType parkType,
        String address,
        double latitude,
        double longitude,
        double areaM2,
        int radiusM
) { }
