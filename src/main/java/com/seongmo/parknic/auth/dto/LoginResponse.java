package com.seongmo.parknic.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, boolean isNewUser) {
}
