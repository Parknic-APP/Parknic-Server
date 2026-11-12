package com.seongmo.parknic.common.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeContractTest {

    @Test
    void 앱과_합의한_에러코드와_HTTP상태() {
        Map<String, HttpStatus> contract = Map.of(
                "CHECKIN_OUT_OF_RANGE", HttpStatus.BAD_REQUEST,
                "CHECKIN_TOO_EARLY", HttpStatus.BAD_REQUEST,
                "CHECKIN_EXPIRED_MIDNIGHT", HttpStatus.BAD_REQUEST,
                "CHECKIN_ALREADY_IN_PROGRESS", HttpStatus.CONFLICT,
                "BADGE_ALREADY_EARNED_TODAY", HttpStatus.CONFLICT,
                "REVIEW_BADGE_REQUIRED", HttpStatus.FORBIDDEN,
                "REVIEW_ALREADY_EXISTS", HttpStatus.CONFLICT,
                "NICKNAME_DUPLICATED", HttpStatus.CONFLICT,
                "NICKNAME_INAPPROPRIATE", HttpStatus.CONFLICT,
                "TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED);

        contract.forEach((name, status) ->
                assertThat(ErrorCode.valueOf(name).getStatus()).isEqualTo(status));
    }

}