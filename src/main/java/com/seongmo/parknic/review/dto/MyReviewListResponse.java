package com.seongmo.parknic.review.dto;

import java.util.List;

public record MyReviewListResponse(
        List<MyReviewItemResponse> reviews,
        int totalCount,
        String nextCursor
) {
}
