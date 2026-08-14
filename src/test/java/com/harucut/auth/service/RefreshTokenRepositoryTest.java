package com.harucut.auth.service;

import com.harucut.auth.jwt.IssuedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class RefreshTokenRepositoryTest {

    private static final String PUBLIC_ID = "test-public-id";
    private static final String SESSION_KEY = "REFRESH_TOKEN:USER:" + PUBLIC_ID;

    private static final String PRESENTED = "presented-token";
    private static final String CANDIDATE = "candidate-token";
    private static final String GRACE_KEY = "REFRESH_GRACE:" + PRESENTED;

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisScript<List> rotateScript;

    private RefreshTokenRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RefreshTokenRepository(redisTemplate, rotateScript);
    }

    @Nested
    @DisplayName("세션 키")
    class Session {

        @Test
        @DisplayName("REFRESH_TOKEN:USER:{publicId}에 TTL과 함께 저장한다")
        void savesWithTtl() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            repository.save(PUBLIC_ID, new IssuedToken("refresh-value", REFRESH_TTL));

            then(valueOperations).should().set(SESSION_KEY, "refresh-value", REFRESH_TTL);
        }

        @Test
        @DisplayName("세션 키를 지운다")
        void deletes() {
            repository.delete(PUBLIC_ID);

            then(redisTemplate).should().delete(SESSION_KEY);
        }
    }

    @Nested
    @DisplayName("rotate")
    class Rotate {

        @Test
        @DisplayName("세션 키와 grace 키를 함께 넘긴다 — grace 키는 제시된 토큰 기준이다")
        void passesBothKeys() {
            givenScriptReturns("ROTATED", CANDIDATE);

            repository.rotate(PUBLIC_ID, PRESENTED, candidate());

            then(redisTemplate).should().execute(
                    rotateScript,
                    List.of(SESSION_KEY, GRACE_KEY),
                    PRESENTED, CANDIDATE, "1209600", "10");
        }

        @Test
        @DisplayName("ROTATED면 스크립트가 저장한 새 토큰을 준다")
        void mapsRotated() {
            givenScriptReturns("ROTATED", CANDIDATE);

            assertThat(repository.rotate(PUBLIC_ID, PRESENTED, candidate()))
                    .isEqualTo(new RotationResult.Rotated(CANDIDATE));
        }

        @Test
        @DisplayName("GRACE면 후보가 아니라 현재 토큰을 준다")
        void mapsGrace() {
            givenScriptReturns("GRACE", "current-token");

            assertThat(repository.rotate(PUBLIC_ID, PRESENTED, candidate()))
                    .isEqualTo(new RotationResult.Graced("current-token"));
        }

        @Test
        @DisplayName("NO_SESSION이면 토큰 없는 결과를 준다")
        void mapsNoSession() {
            givenScriptReturns("NO_SESSION", "");

            assertThat(repository.rotate(PUBLIC_ID, PRESENTED, candidate()))
                    .isEqualTo(new RotationResult.NoSession());
        }

        @Test
        @DisplayName("REUSE면 토큰 없는 결과를 준다")
        void mapsReuse() {
            givenScriptReturns("REUSE", "");

            assertThat(repository.rotate(PUBLIC_ID, PRESENTED, candidate()))
                    .isEqualTo(new RotationResult.ReuseDetected());
        }

        @Test
        @DisplayName("모르는 상태 문자열이면 조용히 통과시키지 않는다")
        void rejectsUnknownStatus() {
            givenScriptReturns("WAT", "");

            assertThatThrownBy(() -> repository.rotate(PUBLIC_ID, PRESENTED, candidate()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private void givenScriptReturns(String status, String token) {
        given(redisTemplate.execute(
                rotateScript,
                List.of(SESSION_KEY, GRACE_KEY),
                PRESENTED, CANDIDATE, "1209600", "10"))
                .willReturn(List.of(status, token));
    }

    private IssuedToken candidate() {
        return new IssuedToken(CANDIDATE, REFRESH_TTL);
    }
}
