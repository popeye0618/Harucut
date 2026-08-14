package com.harucut.auth.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class EmailRateLimit {

    private static final String COOLDOWN_PREFIX = "email:cooldown:";
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    private final StringRedisTemplate redisTemplate;

    public boolean tryAcquireCooldown(String email) {
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key(COOLDOWN_PREFIX, email), "1", COOLDOWN_TTL);

        return Boolean.TRUE.equals(acquired);
    }

    public void releaseCooldown(String email) {
        redisTemplate.delete(key(COOLDOWN_PREFIX, email));
    }

    private String key(String prefix, String email) {
        return prefix + email.toLowerCase(Locale.ROOT);
    }
}
