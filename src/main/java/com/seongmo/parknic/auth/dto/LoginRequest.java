package com.seongmo.parknic.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String token, String authorizationCode) {
}
