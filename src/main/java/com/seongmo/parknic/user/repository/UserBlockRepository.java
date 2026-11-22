package com.seongmo.parknic.user.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Repository
@RequiredArgsConstructor
public class UserBlockRepository {

    private final JdbcClient jdbcClient;

    public void insertIfAbsent(Long blockerId, Long blockedId, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO user_blocks (blocker_id, blocked_id, created_at)
                        VALUES (:blockerId, :blockedId, :createdAt)
                        ON CONFLICT (blocker_id, blocked_id) DO NOTHING
                        """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .param("createdAt", OffsetDateTime.ofInstant(now, ZoneOffset.UTC))
                .update();
    }

    public void delete(Long blockerId, Long blockedId) {
        jdbcClient.sql("""
                        DELETE FROM user_blocks
                        WHERE blocker_id = :blockerId AND blocked_id = :blockedId
                        """)
                .param("blockerId", blockerId)
                .param("blockedId", blockedId)
                .update();
    }
}
