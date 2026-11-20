package com.seongmo.parknic.review.service;

import com.seongmo.parknic.checkin.repository.BadgeRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.park.repository.ParkRepository;
import com.seongmo.parknic.review.dto.CreateReviewRequest;
import com.seongmo.parknic.review.entity.Review;
import com.seongmo.parknic.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final String REVIEW_UNIQUE_CONSTRAINT = "uq_reviews_user_park";

    private final Clock clock;
    private final ReviewRepository reviewRepository;
    private final ParkRepository parkRepository;
    private final BadgeRepository badgeRepository;

    @Transactional
    public void write(Long userId, Long parkId, CreateReviewRequest request) {
        // 공원 존재 -> 없으면 불가
        if (!parkRepository.existsById(parkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        // 배지 보유 -> 하나라도 있으면 가능
        if (!badgeRepository.existsByUserIdAndParkId(userId, parkId)) {
            throw new BusinessException(ErrorCode.REVIEW_BADGE_REQUIRED);
        }

        // 리뷰 중복 -> 쓴 적 있으면 불가
        if (reviewRepository.existsByUserIdAndParkId(userId, parkId)) {
            throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        Review review = Review.write(userId, parkId, request.rating(), request.content(), clock.instant());
        try {
            // 커밋을 기다리지 않고 INSERT를 지금 보내서, 동시 요청이 만든 UNIQUE 위반을 이 안에서 잡도록 함
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            String reason = NestedExceptionUtils.getMostSpecificCause(e).getMessage();
            if (reason != null && reason.contains(REVIEW_UNIQUE_CONSTRAINT)) {
                throw new BusinessException(ErrorCode.REVIEW_ALREADY_EXISTS);
            }
            throw e;
        }
    }
}
