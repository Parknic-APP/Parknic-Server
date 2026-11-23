package com.seongmo.parknic.collection.repository;

import com.seongmo.parknic.collection.dto.CollectionResponse.ParkTypeCount;
import com.seongmo.parknic.park.entity.ParkType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class CollectionQueryRepository {

    private final JdbcClient jdbcClient;

    // 유형별로 배지를 받은 서로 다른 공원 수 (같은 공원에서 여러 번 받아도 1)
    public List<ParkTypeCount> countVisitedParksByType(Long userId) {
        return jdbcClient.sql("""
                        SELECT p.park_type, count(DISTINCT b.park_id) AS park_count
                        FROM badges b
                        JOIN parks p ON p.id = b.park_id
                        WHERE b.user_id = :userId
                        GROUP BY p.park_type
                        ORDER BY park_count DESC, p.park_type
                        """)
                .param("userId", userId)
                .query((rs, rowNum) -> new ParkTypeCount(
                        ParkType.valueOf(rs.getString("park_type")),
                        rs.getInt("park_count")))
                .list();
    }
}
