package com.seongmo.parknic.park.repository;

import com.seongmo.parknic.park.dto.ParkBadgeStatus;
import com.seongmo.parknic.park.dto.ParkSummaryResponse;
import com.seongmo.parknic.park.entity.ParkType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class ParkQueryRepository {

    private static final int MAX_RESULTS = 200;
    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METERS_PER_DEGREE = 111_320;

    private final JdbcClient jdbcClient;

    public List<ParkSummaryResponse> findNearby(double lat, double lng, int radiusM) {
        double latDelta = radiusM / METERS_PER_DEGREE;
        double lngDelta = radiusM / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));

        return jdbcClient.sql("""
                        SELECT id, name, park_type, latitude, longitude, radius_m, distance_m
                        FROM (
                            SELECT id, name, park_type, latitude, longitude, radius_m,
                                   2 * :earthRadius * asin(sqrt(
                                       power(sin(radians(latitude - :lat) / 2), 2)
                                       + cos(radians(:lat)) * cos(radians(latitude))
                                       * power(sin(radians(longitude - :lng) / 2), 2)
                                   )) AS distance_m
                            FROM parks
                            WHERE review_only = FALSE
                              AND latitude BETWEEN :minLat AND :maxLat
                              AND longitude BETWEEN :minLng AND :maxLng
                        ) nearby
                        WHERE distance_m <= :radius
                        ORDER BY distance_m
                        LIMIT :limit
                        """)
                .param("earthRadius", EARTH_RADIUS_M)
                .param("lat", lat)
                .param("lng", lng)
                .param("minLat", lat - latDelta)
                .param("maxLat", lat + latDelta)
                .param("minLng", lng - lngDelta)
                .param("maxLng", lng + lngDelta)
                .param("radius", radiusM)
                .param("limit", MAX_RESULTS)
                .query((rs, rowNum) -> new ParkSummaryResponse(
                        rs.getLong("id"),
                        rs.getString("name"),
                        ParkType.valueOf(rs.getString("park_type")),
                        rs.getDouble("latitude"),
                        rs.getDouble("longitude"),
                        rs.getInt("radius_m"),
                        (int) Math.round(rs.getDouble("distance_m")),
                        ParkBadgeStatus.NONE,
                        0))
                .list();
    }
}
