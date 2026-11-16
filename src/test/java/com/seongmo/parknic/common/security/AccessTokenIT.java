package com.seongmo.parknic.common.security;

import com.seongmo.parknic.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class AccessTokenIT extends IntegrationTest {

    @Autowired
    AccessTokenService accessTokenService;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    JwtProperties jwtProperties;

    @Test
    void 발급한_토큰을_검증하면_subject와_유효기간이_맞다() {
        String token = accessTokenService.issue(1L);

        Jwt jwt = jwtDecoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
                .isEqualTo(jwtProperties.accessTtl());
    }

    @Test
    void 유효기간이_지나면_검증에_실패한다() {
        String token = accessTokenService.issue(1L);

        clock.advance(jwtProperties.accessTtl().plusSeconds(1));

        assertThatThrownBy(() -> jwtDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void 변조된_토큰은_검증에_실패한다() {
        String token = accessTokenService.issue(1L);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThatThrownBy(() -> jwtDecoder.decode(tampered)).isInstanceOf(JwtException.class);
    }
}