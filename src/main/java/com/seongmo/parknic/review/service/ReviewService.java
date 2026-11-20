package com.seongmo.parknic.review.service;

import com.seongmo.parknic.checkin.repository.BadgeRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.park.repository.ParkRepository;
import com.seongmo.parknic.review.dto.CreateReviewRequest;
import com.seongmo.parknic.review.dto.ReviewItemResponse;
import com.seongmo.parknic.review.dto.ReviewListResponse;
import com.seongmo.parknic.review.entity.Review;
import com.seongmo.parknic.review.repository.ReviewQueryRepository;
import com.seongmo.parknic.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final String REVIEW_UNIQUE_CONSTRAINT = "uq_reviews_user_park";
    private static final int PAGE_SIZE = 20;

    private final Clock clock;
    private final ReviewRepository reviewRepository;
    private final ParkRepository parkRepository;
    private final BadgeRepository badgeRepository;
    private final ReviewQueryRepository reviewQueryRepository;

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

    @Transactional(readOnly = true)
    public ReviewListResponse getReviews(Long userId, Long parkId, String cursor) {
        // 공원 존재 -> 없으면 불가
        if (!parkRepository.existsById(parkId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }

        long cursorId = parseCursor(cursor);

        // 다음 페이지가 있는지 알기 위해 한 개 더 가져옴
        List<ReviewItemResponse> rows = reviewQueryRepository.findByPark(userId, parkId, cursorId, PAGE_SIZE + 1);

        boolean hasNext = rows.size() > PAGE_SIZE;
        List<ReviewItemResponse> page = hasNext ? rows.subList(0, PAGE_SIZE) : rows;
        String nextCursor = hasNext ? String.valueOf(page.get(page.size() - 1).id()) : null;

        return new ReviewListResponse(page, nextCursor);
    }

    private long parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(cursor);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
