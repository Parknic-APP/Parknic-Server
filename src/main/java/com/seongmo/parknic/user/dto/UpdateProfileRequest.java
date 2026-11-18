package com.seongmo.parknic.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

public record UpdateProfileRequest(
        @NotNull @Pattern(regexp = "^\\S{2,10}$") String nickname,
        @NotNull @Min(1) @Max(8) Short characterId
) {
}
