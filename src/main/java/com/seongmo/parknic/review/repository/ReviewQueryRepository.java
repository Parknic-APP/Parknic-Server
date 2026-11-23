package com.seongmo.parknic.review.repository;

import com.seongmo.parknic.common.time.Kst;
import com.seongmo.parknic.review.dto.MyReviewItemResponse;
import com.seongmo.parknic.review.dto.ReviewItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ReviewQueryRepository {

    private final JdbcClient jdbcClient;

    public List<ReviewItemResponse> findByPark(Long viewerId, Long parkId, long cursor, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id, r.user_id, u.nickname, u.character_id,
                               COALESCE(b.badge_count, 0) AS badge_count,
                               r.rating, r.content, r.created_at
                        FROM reviews r
                        JOIN users u ON u.id = r.user_id
                        LEFT JOIN (
                            SELECT user_id, count(*) AS badge_count
                            FROM badges
                            WHERE park_id = :parkId
                            GROUP BY user_id 
                        ) b ON b.user_id = r.user_id
                        WHERE r.park_id = :parkId
                        AND r.id < :cursor
                        AND r.hidden = FALSE
                        AND u.status <> 'DELETED'
                        AND NOT EXISTS (
                            SELECT 1 FROM user_blocks ub
                                     WHERE ub.blocker_id = :viewerId AND ub.blocked_id = r.user_id
                        )
                        ORDER BY r.id DESC
                        LIMIT :limit
                        """)
                .param("parkId", parkId)
                .param("viewerId", viewerId)
                .param("cursor", cursor)
                .param("limit", limit)
                .query((rs, rowNum) -> new ReviewItemResponse(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("nickname"),
                        rs.getObject("character_id", Short.class),
                        rs.getInt("badge_count"),
                        rs.getInt("rating"),
                        rs.getString("content"),
                        Kst.format(rs.getObject("created_at", OffsetDateTime.class).toInstant()),
                        rs.getLong("user_id") == viewerId))
                .list();
    }

    // 내가 쓴 리뷰를 최신순으로. 숨겨진 리뷰는 제외
    public List<MyReviewItemResponse> findByUser(Long userId, long cursor, int limit) {
        return jdbcClient.sql("""
                        SELECT r.id, r.park_id, p.name AS park_name, r.rating, r.content, r.created_at
                        FROM reviews r
                        JOIN parks p ON p.id = r.park_id
                        WHERE r.user_id = :userId
                          AND r.id < :cursor
                          AND r.hidden = FALSE
                        ORDER BY r.id DESC
                        LIMIT :limit
                        """)
                .param("userId", userId)
                .param("cursor", cursor)
                .param("limit", limit)
                .query((rs, rowNum) -> new MyReviewItemResponse(
                        rs.getLong("id"),
                        rs.getLong("park_id"),
                        rs.getString("park_name"),
                        rs.getInt("rating"),
                        rs.getString("content"),
                        Kst.format(rs.getObject("created_at", OffsetDateTime.class).toInstant())))
                .list();
    }
}
