package com.seongmo.parknic.checkin.repository;

import com.seongmo.parknic.checkin.entity.CheckinFailReason;
import com.seongmo.parknic.checkin.entity.CheckinSession;
import com.seongmo.parknic.checkin.entity.CheckinStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface CheckinSessionRepository extends JpaRepository<CheckinSession, Long> {

    boolean existsByUserIdAndStatus(Long userId, CheckinStatus status);

    Optional<CheckinSession> findByUserIdAndStatus(Long userId, CheckinStatus status);

    // before 이전에 시작했는데 아직 진행 중인 세션을 한 번에 실패 처리. 바뀐 행 수를 돌려줌
    @Modifying
    @Query("""
            update CheckinSession s
            set s.status = :failed, s.failReason = :reason, s.endedAt = :endedAt
            where s.status = :inProgress and s.startedAt < :before
            """)
    int failExpired(@Param("endedAt") Instant endedAt,
                    @Param("before") Instant before,
                    @Param("inProgress") CheckinStatus inProgress,
                    @Param("failed") CheckinStatus failed,
                    @Param("reason") CheckinFailReason reason);
}
