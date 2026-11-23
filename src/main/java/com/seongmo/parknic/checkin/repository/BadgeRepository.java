package com.seongmo.parknic.checkin.repository;

import com.seongmo.parknic.checkin.entity.Badge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface BadgeRepository extends JpaRepository<Badge, Long> {

    boolean existsByUserIdAndParkIdAndEarnedDate(Long userId, Long parkId, LocalDate earnedDate);

    boolean existsByUserIdAndParkId(Long userId, Long parkId);

    @Query("select count(distinct b.parkId) from Badge b where b.userId = :userId")
    long countDistinctParkByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Query("select distinct b.earnedDate from Badge b where b.userId = :userId order by b.earnedDate")
    List<LocalDate> findEarnedDatesByUserId(@Param("userId") Long userId);
}
