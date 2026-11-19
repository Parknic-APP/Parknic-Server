package com.seongmo.parknic.checkin.repository;

import com.seongmo.parknic.checkin.entity.CheckinSession;
import com.seongmo.parknic.checkin.entity.CheckinStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CheckinSessionRepository extends JpaRepository<CheckinSession, Long> {

    boolean existsByUserIdAndStatus(Long userId, CheckinStatus status);

    Optional<CheckinSession> findByUserIdAndStatus(Long userId, CheckinStatus status);
}
