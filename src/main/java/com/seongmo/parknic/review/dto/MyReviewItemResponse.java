package com.seongmo.parknic.review.dto;

public record MyReviewItemResponse(
        Long id,
        Long parkId,
        String parkName,
        int rating,
        String content,
        String createdAt
) {
}
