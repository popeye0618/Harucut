package com.harucut.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@TestConfiguration
public class FixedClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 22, 10, 0);

    @Bean
    public Clock clock() {
        return Clock.fixed(FIXED_NOW.atZone(ZONE).toInstant(), ZONE);
    }
}
