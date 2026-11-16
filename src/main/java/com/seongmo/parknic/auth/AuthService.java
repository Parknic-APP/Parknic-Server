package com.seongmo.parknic.auth;

import com.seongmo.parknic.auth.dto.LoginRequest;
import com.seongmo.parknic.auth.dto.LoginResponse;
import com.seongmo.parknic.common.error.BusinessException;
import com.seongmo.parknic.common.error.ErrorCode;
import com.seongmo.parknic.common.security.AccessTokenService;
import com.seongmo.parknic.user.User;
import com.seongmo.parknic.user.UserRepository;
import com.seongmo.parknic.user.UserStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEV_PREFIX = "dev:";
    private static final String DEV_PROVIDER = "DEV";
    private static final String TEMP_REFRESH_TOKEN = "TODO-refresh-token"; // Refresh 토큰 작업에서 교체

    private final UserRepository userRepository;
    private final AccessTokenService accessTokenService;
    private final Clock clock;
    private final DevLoginProperties devLoginProperties;

    @Transactional
    public LoginResponse loginApple(LoginRequest request) {
        String name = extractDevName(request.token());

        User user = userRepository.findByProviderAndProviderId(DEV_PROVIDER, name)
                .orElseGet(() -> userRepository.save(User.pending(DEV_PROVIDER, name, clock.instant())));

        String accessToken = accessTokenService.issue(user.getId());
        boolean isNewUser = user.getStatus() == UserStatus.PENDING;
        return new LoginResponse(accessToken, TEMP_REFRESH_TOKEN, isNewUser);
    }

    private String extractDevName(String token) {
        if (!devLoginProperties.enabled() || !token.startsWith(DEV_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String name = token.substring(token.lastIndexOf(':') + 1);
        if (name.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return name;
    }
}
