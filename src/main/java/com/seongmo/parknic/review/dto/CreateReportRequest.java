package com.seongmo.parknic.review.dto;

import com.seongmo.parknic.review.entity.ReportReason;
import jakarta.validation.constraints.NotNull;

public record CreateReportRequest(
        @NotNull ReportReason reason
        ) {
}
