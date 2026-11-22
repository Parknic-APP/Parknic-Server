package com.seongmo.parknic.ranking.repository;

import com.seongmo.parknic.ranking.dto.RankingEntryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class RankingQueryRepository {

    private final JdbcClient jdbcClient;

    public List<RankingEntryResponse> findWeekly(Long parkId, Long viewerId,
                                                 LocalDate weekStart, LocalDate weekEnd, int limit) {
        return jdbcClient.sql("""
                        SELECT ranked.ranking, ranked.user_id, ranked.nickname,
                               ranked.character_id, ranked.badge_count
                        FROM (
                            SELECT ROW_NUMBER() OVER (
                                       ORDER BY count(*) DESC, max(b.earned_at) ASC, b.user_id ASC
                                   ) AS ranking,
                                   b.user_id, u.nickname, u.character_id,
                                   count(*) AS badge_count
                            FROM badges b
                            JOIN users u ON u.id = b.user_id
                            WHERE b.park_id = :parkId
                              AND b.earned_date BETWEEN :weekStart AND :weekEnd
                              AND u.status = 'ACTIVE'
                            GROUP BY b.user_id, u.nickname, u.character_id
                        ) ranked
                        WHERE ranked.ranking <= :limit OR ranked.user_id = :viewerId
                        ORDER BY ranked.ranking
                        """)
                .param("parkId", parkId)
                .param("viewerId", viewerId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEnd)
                .param("limit", limit)
                .query((rs, rowNum) -> new RankingEntryResponse(
                        rs.getInt("ranking"),
                        rs.getLong("user_id"),
                        rs.getString("nickname"),
                        rs.getObject("character_id", Short.class),
                        rs.getInt("badge_count")))
                .list();
    }
}
