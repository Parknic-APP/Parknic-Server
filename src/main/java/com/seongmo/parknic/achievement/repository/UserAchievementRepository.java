package com.seongmo.parknic.achievement.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
}
