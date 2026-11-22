package com.seongmo.parknic.review.repository;

import com.seongmo.parknic.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUserIdAndParkId(Long userId, Long parkId);

    Optional<Review> findByIdAndUserIdAndHiddenFalse(Long id, Long userId);
}
