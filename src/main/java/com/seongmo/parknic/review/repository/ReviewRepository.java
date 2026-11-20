package com.seongmo.parknic.review.repository;

import com.seongmo.parknic.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByUserIdAndParkId(Long userId, Long parkId);
}
