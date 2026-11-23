package com.seongmo.parknic.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "parknic.apple")
public record AppleProperties(
        @NotBlank String clientId,
        @DefaultValue("https://appleid.apple.com/auth/keys") String jwksUri,
        // 아래 세 값은 Apple 토큰 교환/폐기에 필요한 client secret을 만들 때만 쓴다. 비어 있으면 그 기능을 건너뛴다
        @DefaultValue("") String teamId,
        @DefaultValue("") String keyId,
        @DefaultValue("") String privateKey,
        @DefaultValue("https://appleid.apple.com/auth/token") String tokenUri,
        @DefaultValue("https://appleid.apple.com/auth/revoke") String revokeUri
) {

    public boolean signingConfigured() {
        return StringUtils.hasText(teamId) && StringUtils.hasText(keyId) && StringUtils.hasText(privateKey);
    }
}
