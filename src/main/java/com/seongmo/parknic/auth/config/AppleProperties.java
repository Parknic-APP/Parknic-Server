package com.seongmo.parknic.auth.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "parknic.apple")
public record AppleProperties(
        @NotBlank String clientId,
        @DefaultValue("https://appleid.apple.com/auth/keys") String jwksUri
) {
}
