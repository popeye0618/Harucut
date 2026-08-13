package com.harucut.auth.email;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class EmailVerificationRepository {

    private static final String CODE_PREFIX = "email:code:";
    private static final String VERIFIED_PREFIX = "email:verified:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

    private static final String VERIFIED_VALUE = "VERIFIED";

    private final StringRedisTemplate redisTemplate;


    public void saveCode(String email, String code) {
        redisTemplate.opsForValue().set(key(CODE_PREFIX, email), code, CODE_TTL);
    }

    public Optional<String> findCode(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key(CODE_PREFIX, email)));
    }

    public void removeCode(String email) {
        redisTemplate.delete(key(CODE_PREFIX, email));
    }

    public void markVerified(String email) {
        redisTemplate.opsForValue()
                .set(key(VERIFIED_PREFIX, email), VERIFIED_VALUE, VERIFIED_TTL);
    }

    public boolean consumeVerified(String email) {
        return Boolean.TRUE.equals(redisTemplate.delete(key(VERIFIED_PREFIX, email)));
    }

    private String key(String prefix, String email) {
        return prefix + email.toLowerCase(Locale.ROOT);
    }
}
