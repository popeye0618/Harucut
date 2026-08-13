package com.harucut.auth.password;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PasswordResetRepository {

    private static final String CODE_PREFIX = "email:reset:code:";
    private static final String TOKEN_PREFIX = "reset:token:";

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void saveCode(String email, String code) {
        redisTemplate.opsForValue().set(codeKey(email), code, CODE_TTL);
    }

    public Optional<String> findCode(String email) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(codeKey(email)));
    }

    public void removeCode(String email) {
        redisTemplate.delete(codeKey(email));
    }

    public void saveToken(String token, String email) {
        redisTemplate.opsForValue().set(TOKEN_PREFIX + token, email, TOKEN_TTL);
    }

    public Optional<String> consumeToken(String token) {
        return Optional.ofNullable(redisTemplate.opsForValue().getAndDelete(TOKEN_PREFIX + token));
    }

    private String codeKey(String email) {
        return CODE_PREFIX + email.toLowerCase(Locale.ROOT);
    }
}
