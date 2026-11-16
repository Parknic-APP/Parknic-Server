package com.seongmo.parknic.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DevLoginProperties.class)
public class AuthConfig {
}
