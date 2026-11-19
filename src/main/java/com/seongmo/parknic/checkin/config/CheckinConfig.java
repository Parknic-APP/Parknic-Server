package com.seongmo.parknic.checkin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CheckinProperties.class)
public class CheckinConfig {
}
