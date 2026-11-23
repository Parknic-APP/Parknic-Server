package com.seongmo.parknic.auth.service;

import com.seongmo.parknic.auth.config.DevLoginProperties;
import com.seongmo.parknic.auth.dto.LoginRequest;
import com.seongmo.parknic.auth.dto.LoginResponse;
import com.seongmo.parknic.auth.dto.ReissueRequest;
import com.seongmo.parknic.auth.dto.ReissueResponse;
import com.seongmo.parknic.auth.entity.RefreshToken;
import com.seongmo.parknic.auth.repository.RefreshTokenRepository;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.security.AccessTokenService;
import com.seongmo.parknic.user.entity.User;
import com.seongmo.parknic.user.entity.UserStatus;
import com.seongmo.parknic.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEV_PREFIX = "dev:";
    private static final String DEV_PROVIDER = "DEV";
    private static final String APPLE_PROVIDER = "APPLE";
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final UserRepository userRepository;
    private final AccessTokenService accessTokenService;
    private final Clock clock;
    private final DevLoginProperties devLoginProperties;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppleTokenVerifier appleTokenVerifier;

    @Transactional
    public LoginResponse loginApple(LoginRequest request) {
        Identity identity = resolveIdentity(request.token());

        User user = userRepository.findByProviderAndProviderId(identity.provider(), identity.providerId())
                .orElseGet(() -> userRepository.save(
                        User.pending(identity.provider(), identity.providerId(), clock.instant())));

        if (user.getStatus() == UserStatus.DELETED) {
            user.rejoin();
        }
        String accessToken = accessTokenService.issue(user.getId());
        String refreshToken = issueRefreshToken(user.getId(), UUID.randomUUID());
        boolean isNewUser = user.getStatus() == UserStatus.PENDING;
        return new LoginResponse(accessToken, refreshToken, isNewUser);
    }

    private String issueRefreshToken(Long userId, UUID familyId) {
        String rawToken = refreshTokenGenerator.generate();
        Instant expiresAt = clock.instant().plus(REFRESH_TOKEN_TTL);
        refreshTokenRepository.save(RefreshToken.issue(
                userId, familyId, refreshTokenGenerator.hash(rawToken), expiresAt));
        return rawToken;
    }

    // 개발용 로그인이 켜져 있고 dev: 토큰이면 개발용, 그 외에는 Apple 토큰으로 보고 검증
    private Identity resolveIdentity(String token) {
        if (devLoginProperties.enabled() && token.startsWith(DEV_PREFIX)) {
            return new Identity(DEV_PROVIDER, extractDevName(token));
        }
        return new Identity(APPLE_PROVIDER, appleTokenVerifier.verify(token));
    }

    private String extractDevName(String token) {
        String name = token.substring(token.lastIndexOf(':') + 1);
        if (name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return name;
    }

    @Transactional
    public ReissueResponse reissue(ReissueRequest request) {
        String tokenHash = refreshTokenGenerator.hash(request.refreshToken());
        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        if (current.isExpired(clock.instant())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        User user = userRepository.findByIdAndStatusNot(current.getUserId(), UserStatus.DELETED)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 거절 검사를 모두 끝낸 뒤 마지막에 "아직 안 쓴 토큰이면 쓴 것으로 바꾸기"를 한 번에 처리.
        // 동시에 같은 토큰이 오면 DB가 한 요청만 1행을 바꾸게 하므로 나머지는 0행이 되어 거절됨
        if (refreshTokenRepository.markUsedIfUnused(current.getId()) == 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String accessToken = accessTokenService.issue(user.getId());
        String refreshToken = issueRefreshToken(user.getId(), current.getFamilyId());
        return new ReissueResponse(accessToken, refreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    // 로그인한 소셜 계정을 식별하는 값 (provider + provider가 준 고유 ID)
    private record Identity(String provider, String providerId) { }
}
