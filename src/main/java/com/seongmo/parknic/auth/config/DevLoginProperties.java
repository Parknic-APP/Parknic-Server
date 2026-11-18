package com.seongmo.parknic.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "parknic.dev-login")
public record DevLoginProperties(@DefaultValue("false") boolean enabled) {
}
