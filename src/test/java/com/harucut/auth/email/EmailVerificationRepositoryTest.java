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
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EmailVerificationRepositoryTest {

    private static final String EMAIL = "user@harucut.com";
    private static final String CODE = "ABC234";

    private static final String CODE_KEY = "email:code:" + EMAIL;
    private static final String VERIFIED_KEY = "email:verified:" + EMAIL;

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private EmailVerificationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new EmailVerificationRepository(redisTemplate);
    }

    @Nested
    @DisplayName("코드")
    class Code {

        @Test
        @DisplayName("email:code 키에 5분 TTL로 저장한다")
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
        @DisplayName("email:code 키를 지운다")
        void removes() {
            repository.removeCode(EMAIL);

            then(redisTemplate).should().delete(CODE_KEY);
        }
    }

    @Nested
    @DisplayName("검증 플래그")
    class VerifiedFlag {

        @Test
        @DisplayName("email:verified 키에 VERIFIED를 10분 TTL로 심는다")
        void marksWithTtl() {
            givenValueOperations();

            repository.markVerified(EMAIL);

            then(valueOperations).should().set(VERIFIED_KEY, "VERIFIED", VERIFIED_TTL);
        }

        @Test
        @DisplayName("키가 있으면 true — 조회는 지우지 않는다")
        void verifiedWhenKeyExists() {
            given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(true);

            assertThat(repository.isVerified(EMAIL)).isTrue();
            then(redisTemplate).should(never()).delete(VERIFIED_KEY);
        }

        @Test
        @DisplayName("키가 없으면 false")
        void notVerifiedWhenMissing() {
            given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(false);

            assertThat(repository.isVerified(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("Redis가 null을 반환해도 false로 처리한다")
        void treatsNullAsFalse() {
            given(redisTemplate.hasKey(VERIFIED_KEY)).willReturn(null);

            assertThat(repository.isVerified(EMAIL)).isFalse();
        }

        @Test
        @DisplayName("email:verified 키를 지운다")
        void removesVerified() {
            repository.removeVerified(EMAIL);

            then(redisTemplate).should().delete(VERIFIED_KEY);
        }
    }

    private void givenValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

}