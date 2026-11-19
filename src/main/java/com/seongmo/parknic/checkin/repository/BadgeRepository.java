package com.seongmo.parknic.checkin.repository;

import com.seongmo.parknic.checkin.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    boolean existsByUserIdAndParkIdAndEarnedDate(Long userId, Long parkId, LocalDate earnedDate);
}
