package com.seongmo.parknic.review.dto;

import jakarta.validation.constraints.*;

public record UpdateReviewRequest(
        @NotNull @Min(1) @Max(5) Integer rating,
        @NotBlank @Size(min = 10, max = 300) String content
) {
}
