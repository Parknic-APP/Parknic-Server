package com.seongmo.parknic.review.controller;

import com.seongmo.parknic.common.security.CurrentUserId;
import com.seongmo.parknic.review.dto.CreateReviewRequest;
import com.seongmo.parknic.review.dto.ReviewListResponse;
import com.seongmo.parknic.review.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/parks/{parkId}/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public void write(@CurrentUserId Long userId, @PathVariable Long parkId, @Valid @RequestBody CreateReviewRequest request) {
        reviewService.write(userId, parkId, request);
    }

    @GetMapping
    public ReviewListResponse getReviews(@CurrentUserId Long userId, @PathVariable Long parkId, @RequestParam(required = false) String cursor) {
        return reviewService.getReviews(userId, parkId, cursor);
    }
}
