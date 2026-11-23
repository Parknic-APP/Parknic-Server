package com.seongmo.parknic.user.repository;

import com.seongmo.parknic.ranking.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class UserStatsQueryRepository {

    private final JdbcClient jdbcClient;

    // 이번 주 공원별 랭킹에서 내가 1~3위인 공원 수
    public int countPodiumParks(Long userId, LocalDate weekStart, LocalDate weekEnd) {
        return jdbcClient.sql("""
                        SELECT count(*)
                        FROM (
                            SELECT b.user_id,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY b.park_id
                                       ORDER BY count(*) DESC, max(b.earned_at) ASC, b.user_id ASC
                                   ) AS ranking
                            FROM badges b
                            JOIN users u ON u.id = b.user_id
                            WHERE b.park_id IN (
                                      SELECT park_id FROM badges
                                      WHERE user_id = :userId
                                        AND earned_date BETWEEN :weekStart AND :weekEnd
                                  )
                              AND b.earned_date BETWEEN :weekStart AND :weekEnd
                              AND u.status = 'ACTIVE'
                            GROUP BY b.park_id, b.user_id
                        ) ranked
                        WHERE ranked.user_id = :userId AND ranked.ranking <= :podiumSize
                        """)
                .param("userId", userId)
                .param("weekStart", weekStart)
                .param("weekEnd", weekEnd)
                .param("podiumSize", RankingService.PODIUM_SIZE)
                .query(Integer.class)
                .single();
    }
}
