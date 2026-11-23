package com.seongmo.parknic.auth.service;

import com.seongmo.parknic.auth.config.AppleProperties;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

// 앱이 Sign in with Apple로 받은 identity token(JWT)을 검증하고 Apple 사용자 고유 ID(sub)를 돌려준다
@Slf4j
@Component
public class AppleTokenVerifier {

    private static final String ISSUER = "https://appleid.apple.com";

    private final JwtDecoder decoder;

    public AppleTokenVerifier(AppleProperties properties) {
        // 서명은 Apple의 공개키 목록(JWKS)으로 RS256만 허용해서 검증. 공개키는 라이브러리가 캐시한다
        NimbusJwtDecoder nimbusDecoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();

        // exp(만료)와 iss(발급자)는 기본 검증, aud(이 토큰이 우리 앱용인지)는 직접 검증
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(properties.clientId())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "aud가 우리 앱이 아님", null));
        nimbusDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER), audienceValidator));

        this.decoder = nimbusDecoder;
    }

    // 검증에 성공하면 sub를 돌려주고, 실패하면 401(UNAUTHORIZED)
    public String verify(String identityToken) {
        try {
            String subject = decoder.decode(identityToken).getSubject();
            if (subject == null || subject.isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return subject;
        } catch (JwtException e) {
            log.warn("Apple 토큰 검증 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }
}
