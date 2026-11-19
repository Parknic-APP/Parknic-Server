package com.seongmo.parknic.checkin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "parknic.checkin")
public record CheckinProperties(@DefaultValue("1200") long requiredSeconds) {
}
