package com.seongmo.parknic.review.dto;

import java.util.List;

public record ReviewListResponse(
        List<ReviewItemResponse> reviews,
        String nextCursor
) {
}
