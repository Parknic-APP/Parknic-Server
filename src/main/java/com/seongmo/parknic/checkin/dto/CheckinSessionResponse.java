package com.seongmo.parknic.checkin.dto;

import com.seongmo.parknic.checkin.entity.CheckinSession;
import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.park.entity.Park;

import java.time.OffsetDateTime;

public record CheckinSessionResponse(
        Long id,
        Long parkId,
        String parkName,
        double latitude,
        double longitude,
        int radiusM,
        OffsetDateTime startedAt
) {

    public static CheckinSessionResponse of(CheckinSession session, Park park) {
        return new CheckinSessionResponse(
                session.getId(),
                park.getId(),
                park.getName(),
                park.getLatitude(),
                park.getLongitude(),
                park.getRadiusM(),
                session.getStartedAt().atZone(Kst.ZONE).toOffsetDateTime()
        );
    }
}
