package com.seongmo.parknic.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "parknic.jwt")
public record JwtProperties(@NotBlank @Size(min = 32) String secret, @DefaultValue("30m") Duration accessTtl) {
}
