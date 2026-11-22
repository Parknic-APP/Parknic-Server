package com.seongmo.parknic.review.service;

import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.review.dto.CreateReportRequest;
import com.seongmo.parknic.review.entity.Review;
import com.seongmo.parknic.review.entity.ReviewReport;
import com.seongmo.parknic.review.repository.ReviewReportRepository;
import com.seongmo.parknic.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ReviewReportService {

    private static final int REPORT_HIDE_THRESHOLD = 5;
    private static final String REPORT_UNIQUE_CONSTRAINT = "uq_report_review_reporter";

    private final ReviewRepository reviewRepository;
    private final ReviewReportRepository reviewReportRepository;
    private final Clock clock;

    @Transactional
    public void report(Long userId, Long reviewId, CreateReportRequest request) {
        // 리뷰 행에 락을 걸어서 같은 리뷰의 신고 처리를 한 줄로 세움
        Review review = reviewRepository.findWithLockByIdAndHiddenFalse(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        // 내 리뷰는 신고 불가
        if (review.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 이미 신고했으면 불가
        if (reviewReportRepository.existsByReviewIdAndReporterId(reviewId, userId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_REPORTED);
        }

        try {
            reviewReportRepository.saveAndFlush(
                    ReviewReport.report(reviewId, userId, request.reason(), clock.instant()));
        } catch (DataIntegrityViolationException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (reason != null && reason.contains(REPORT_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.REVIEW_ALREADY_REPORTED);
            }
            throw e;
        }

        // 방금 저장한 신고까지 센 개수가 기준 이상이면 숨김
        if (reviewReportRepository.countByReviewId(reviewId) >= REPORT_HIDE_THRESHOLD) {
            review.hide();
        }
    }
}
