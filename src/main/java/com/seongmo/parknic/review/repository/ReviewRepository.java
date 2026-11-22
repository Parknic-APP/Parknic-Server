package com.seongmo.parknic.review.repository;

import com.seongmo.parknic.review.entity.Review;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUserIdAndParkId(Long userId, Long parkId);

    Optional<Review> findByIdAndUserIdAndHiddenFalse(Long id, Long userId);

    @Modifying
    @Query("delete from Review r where r.id = :id and r.userId = :userId")
    int deleteByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Review> findWithLockByIdAndHiddenFalse(Long id);
}
