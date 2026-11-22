package com.seongmo.parknic.review.repository;

import com.seongmo.parknic.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);

    long countByReviewId(Long reviewId);
}
