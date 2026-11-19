package com.seongmo.parknic.checkin.dto;

import com.seongmo.parknic.checkin.entity.CheckinFailReason;
import jakarta.validation.constraints.NotNull;

public record FailCheckinRequest(
        @NotNull CheckinFailReason reason
) {
}
