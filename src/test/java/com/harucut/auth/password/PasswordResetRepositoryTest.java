package com.harucut.auth.password;

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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PasswordResetRepositoryTest {

    private static final String EMAIL = "user@harucut.com";
    private static final String CODE = "ABC234";
    private static final String TOKEN = "550e8400-e29b-41d4-a716-446655440000";

    private static final String CODE_KEY = "email:reset:code:" + EMAIL;
    private static final String TOKEN_KEY = "reset:token:" + TOKEN;

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private PasswordResetRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PasswordResetRepository(redisTemplate);
    }

    @Nested
    @DisplayName("코드")
    class Code {

        @Test
        @DisplayName("email:reset:code 키에 5분 TTL로 저장한다")
        void savesWithTtl() {
            givenValueOperations();

            repository.saveCode(EMAIL, CODE);

            then(valueOperations).should().set(CODE_KEY, CODE, CODE_TTL);
        }

        @Test
        @DisplayName("대문자 이메일로 들어와도 키는 소문자로 만든다")
        void lowercasesKey() {
            givenValueOperations();

            repository.saveCode("User@Harucut.com", CODE);

            then(valueOperations).should().set(CODE_KEY, CODE, CODE_TTL);
        }

        @Test
        @DisplayName("없으면 빈 Optional을 준다")
        void missingCodeIsEmpty() {
            givenValueOperations();
            given(valueOperations.get(CODE_KEY)).willReturn(null);

            assertThat(repository.findCode(EMAIL)).isEmpty();
        }

        @Test
        @DisplayName("email:reset:code 키를 지운다")
        void removes() {
            repository.removeCode(EMAIL);

            then(redisTemplate).should().delete(CODE_KEY);
        }
    }

    @Nested
    @DisplayName("리셋 토큰")
    class Token {

        @Test
        @DisplayName("reset:token 키에 email을 10분 TTL로 저장한다")
        void savesWithTtl() {
            givenValueOperations();

            repository.saveToken(TOKEN, EMAIL);

            then(valueOperations).should().set(TOKEN_KEY, EMAIL, TOKEN_TTL);
        }

        @Test
        @DisplayName("소비는 GETDEL 한 번으로 한다 — 조회와 삭제를 나누지 않는다")
        void consumesAtomically() {
            givenValueOperations();
            given(valueOperations.getAndDelete(TOKEN_KEY)).willReturn(EMAIL);

            assertThat(repository.consumeToken(TOKEN)).contains(EMAIL);

            then(valueOperations).should().getAndDelete(TOKEN_KEY);
            then(redisTemplate).should(never()).delete(TOKEN_KEY);
        }

        @Test
        @DisplayName("이미 소비됐거나 만료됐으면 빈 Optional을 준다")
        void consumedTokenIsEmpty() {
            givenValueOperations();
            given(valueOperations.getAndDelete(TOKEN_KEY)).willReturn(null);

            assertThat(repository.consumeToken(TOKEN)).isEmpty();
        }
    }

    private void givenValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

}