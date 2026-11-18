package com.seongmo.parknic.user.dto;

import java.time.OffsetDateTime;

public record UserProfileResponse(
        String nickname,
        Short characterId,
        String characterName,
        OffsetDateTime createdAt) { }
