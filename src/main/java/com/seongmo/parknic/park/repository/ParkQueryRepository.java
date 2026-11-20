package com.seongmo.parknic.park.repository;

import com.seongmo.parknic.park.dto.ParkBadgeStatus;
import com.seongmo.parknic.park.dto.ParkMyBadge;
import com.seongmo.parknic.park.dto.ParkSummaryResponse;
import com.seongmo.parknic.park.entity.ParkType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ParkQueryRepository {

    private static final int MAX_RESULTS = 200;
    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METERS_PER_DEGREE = 111_320;

    private final JdbcClient jdbcClient;

    public List<ParkSummaryResponse> findNearby(Long userId, LocalDate today, double lat, double lng, int radiusM) {
        double latDelta = radiusM / METERS_PER_DEGREE;
        double lngDelta = radiusM / (METERS_PER_DEGREE * Math.cos(Math.toRadians(lat)));

        return jdbcClient.sql("""
                        SELECT nearby.id, nearby.name, nearby.park_type, nearby.latitude, nearby.longitude,
                                                   nearby.radius_m, nearby.distance_m,
                                                   COALESCE(mb.badge_count, 0) AS badge_count,
                                                   COALESCE(mb.earned_today, FALSE) AS earned_today
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
                                            LEFT JOIN (
                                                SELECT park_id,
                                                       count(*) AS badge_count,
                                                       bool_or(earned_date = :today) AS earned_today
                                                FROM badges
                                                WHERE user_id = :userId
                                                GROUP BY park_id
                                            ) mb ON mb.park_id = nearby.id
                                            WHERE nearby.distance_m <= :radius
                                            ORDER BY nearby.distance_m
                                            LIMIT :limit
                        """)
                .param("earthRadius", EARTH_RADIUS_M)
                .param("lat", lat)
                .param("lng", lng)
                .param("minLat", lat - latDelta)
                .param("maxLat", lat + latDelta)
                .param("minLng", lng - lngDelta)
                .param("maxLng", lng + lngDelta)
                .param("userId", userId)
                .param("today", today)
                .param("radius", radiusM)
                .param("limit", MAX_RESULTS)
                .query((rs, rowNum) -> {
                    long badgeCount = rs.getLong("badge_count");
                    return new ParkSummaryResponse(
                            rs.getLong("id"),
                            rs.getString("name"),
                            ParkType.valueOf(rs.getString("park_type")),
                            rs.getDouble("latitude"),
                            rs.getDouble("longitude"),
                            rs.getInt("radius_m"),
                            (int) Math.round(rs.getDouble("distance_m")),
                            ParkBadgeStatus.of(badgeCount, rs.getBoolean("earned_today")),
                            (int) badgeCount);
                })
                .list();
    }

    public ParkMyBadge findMyBadge(Long userId, Long parkId, LocalDate today) {
        return jdbcClient.sql("""
                        SELECT count(*) AS badge_count,
                               COALESCE(bool_or(earned_date = :today), FALSE) AS earned_today
                        FROM badges
                        WHERE user_id = :userId AND park_id = :parkId
                        """)
                .param("userId", userId)
                .param("parkId", parkId)
                .param("today", today)
                .query((rs, rowNum) -> {
                    long badgeCount = rs.getLong("badge_count");
                    return new ParkMyBadge(
                            ParkBadgeStatus.of(badgeCount, rs.getBoolean("earned_today")),
                            (int) badgeCount);
                })
                .single();
    }
}
