package com.seongmo.parknic.achievement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class UserAchievementRepository {

    private final JdbcClient jdbcClient;

    // 이번에 처음 들어갔으면 true, 이미 있었으면 false
    public boolean insertIfAbsent(Long userId, String code, Instant now) {
        int inserted = jdbcClient.sql("""
                        INSERT INTO user_achievements (user_id, achievement_cd, achieved_at)
                        VALUES (:userId, :code, :achievedAt)
                        ON CONFLICT (user_id, achievement_cd) DO NOTHING
                        """)
                .param("userId", userId)
                .param("code", code)
                .param("achievedAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
        return inserted == 1;
    }

    // 사용자가 달성한 업적의 코드 -> 달성 시각 (한 번의 조회로 가져와서 업적마다 조회하지 않음)
    public Map<String, Instant> findAchievedAtByUserId(Long userId) {
        return jdbcClient.sql("""
                        SELECT achievement_cd, achieved_at
                        FROM user_achievements
                        WHERE user_id = :userId
                        """)
                .param("userId", userId)
                .query(rs -> {
                    Map<String, Instant> achievedAt = new HashMap<>();
                    while (rs.next()) {
                        achievedAt.put(
                                rs.getString("achievement_cd"),
                                rs.getObject("achieved_at", OffsetDateTime.class).toInstant());
                    }
                    return achievedAt;
                });
    }
}
