package com.harucut.auth.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class EmailRateLimitTest {

    private static final String EMAIL = "user@harucut.com";
    private static final String COOLDOWN_KEY = "email:cooldown:" + EMAIL;
    private static final Duration COOLDOWN_TTL = Duration.ofSeconds(60);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailRateLimit rateLimit;

    @BeforeEach
    void setUp() {
        rateLimit = new EmailRateLimit(redisTemplate);
    }

    @Nested
    @DisplayName("tryAcquireCooldown")
    class TryAcquireCooldown {

        @Test
        @DisplayName("email:cooldown 키를 60초 TTL로 선점한다")
        void setsKeyWithTtl() {
            givenValueOperations();

            rateLimit.tryAcquireCooldown(EMAIL);

            then(valueOperations).should().setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN_TTL);
        }

        @Test
        @DisplayName("대문자 이메일로 들어와도 키는 소문자로 만든다")
        void lowercasesKey() {
            givenValueOperations();

            rateLimit.tryAcquireCooldown("User@Harucut.com");

            then(valueOperations).should().setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN_TTL);
        }

        @Test
        @DisplayName("선점에 성공하면 true를 반환한다")
        void returnsTrueWhenAcquired() {
            givenSetIfAbsentReturns(true);

            assertThat(rateLimit.tryAcquireCooldown(EMAIL)).isTrue();
        }

        @Test
        @DisplayName("이미 키가 있으면 false를 반환한다")
        void returnsFalseWhenAlreadyPresent() {
            givenSetIfAbsentReturns(false);

            assertThat(rateLimit.tryAcquireCooldown(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("Redis가 null을 반환해도 false로 처리한다")
        void treatsNullAsFalse() {
            givenSetIfAbsentReturns(null);

            assertThat(rateLimit.tryAcquireCooldown(EMAIL)).isFalse();
        }
    }

    @Nested
    @DisplayName("releaseCooldown")
    class ReleaseCooldown {

        @Test
        @DisplayName("email:cooldown 키를 지운다")
        void deletesKey() {
            rateLimit.releaseCooldown(EMAIL);

            then(redisTemplate).should().delete(COOLDOWN_KEY);
        }
    }

    private void givenValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    private void givenSetIfAbsentReturns(Boolean result) {
        givenValueOperations();
        given(valueOperations.setIfAbsent(COOLDOWN_KEY, "1", COOLDOWN_TTL)).willReturn(result);
    }
}