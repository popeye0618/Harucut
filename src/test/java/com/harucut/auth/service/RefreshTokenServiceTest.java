package com.harucut.auth.service;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.cookie.CookieProperties;
import com.harucut.auth.dto.AuthTokenCookies;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import com.harucut.support.FixedClockConfig;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String SECRET =
            "test-secret-key-must-be-at-least-256-bits-for-hs256-algorithm-aa";
    private static final String PUBLIC_ID = "test-public-id";
    private static final String KEY = "REFRESH_TOKEN:USER:" + PUBLIC_ID;
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private static final Clock CLOCK = Clock.fixed(
            FixedClockConfig.FIXED_NOW.atZone(FixedClockConfig.ZONE).toInstant(),
            FixedClockConfig.ZONE);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtTokenService jwtTokenService;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(properties(), CLOCK);
        refreshTokenService = new RefreshTokenService(
                redisTemplate,
                jwtTokenService,
                new CookieManager(new CookieProperties("localhost", false, "Lax")));
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("REFRESH_TOKEN:USER:{publicId} 키에 TTL과 함께 저장한다")
        void saveWithTTL() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);

            refreshTokenService.save(PUBLIC_ID, new IssuedToken("refresh-value", REFRESH_TTL));

            then(valueOperations).should().set(KEY, "refresh-value", REFRESH_TTL);
        }
    }

    @Nested
    @DisplayName("reissue")
    class Reissue {

        @Test
        @DisplayName("저장값과 일치하면 새 토큰으로 회전하고 쿠키 2개를 돌려준다")
        void rotates() {
            String oldRefreshToken = oldRefreshToken();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(KEY)).willReturn(oldRefreshToken);
            AuthTokenCookies cookies = refreshTokenService.reissue(oldRefreshToken);

            ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
            then(valueOperations).should().set(eq(KEY), saved.capture(), eq(REFRESH_TTL));

            assertThat(saved.getValue()).isNotEqualTo(oldRefreshToken);
            assertThat(cookies.accessTokenCookie().getName()).isEqualTo("accessToken");
            assertThat(jwtTokenService.parse(cookies.accessTokenCookie().getValue()).type()).isEqualTo(TokenType.ACCESS);
        }

        @Test
        @DisplayName("저장값과 다르면 재사용으로 보고 키를 지운 뒤 AUTH-011을 던진다")
        void replayDetected() {
            String oldRefreshToken = oldRefreshToken();
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(KEY)).willReturn(jwtTokenService.createRefreshToken(PUBLIC_ID).value());

            assertThatThrownBy(() -> refreshTokenService.reissue(oldRefreshToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            then(redisTemplate).should().delete(KEY);
        }

        @Test
        @DisplayName("저장값이 없으면 지울 세션이 없으므로 삭제하지 않고 AUTH-011만 던진다")
        void noStoredToken() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(KEY)).willReturn(null);

            assertThatThrownBy(() -> refreshTokenService.reissue(oldRefreshToken()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            then(redisTemplate).should(never()).delete(KEY);
        }

        @Test
        @DisplayName("access 토큰을 넣으면 Redis를 보지도 않고 AUTH-011을 던진다")
        void accessTokenRejected() {
            String accessToken = jwtTokenService.createAccessToken(PUBLIC_ID).value();

            assertThatThrownBy(() -> refreshTokenService.reissue(accessToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            then(redisTemplate).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("logout")
    class Logout {

        @Test
        @DisplayName("키를 지운다")
        void deletesKey() {
            refreshTokenService.logout(PUBLIC_ID);

            then(redisTemplate).should().delete(KEY);
        }

        @Test
        @DisplayName("Redis가 죽어도 예외를 삼킨다")
        void survivesRedisFailure() {
            given(redisTemplate.delete(KEY))
                    .willThrow(new RedisConnectionFailureException("down"));

            assertThatCode(() -> refreshTokenService.logout(PUBLIC_ID))
                    .doesNotThrowAnyException();
        }
    }

    private String oldRefreshToken() {
        return new JwtTokenService(properties(), Clock.offset(CLOCK, Duration.ofMinutes(-1)))
                .createRefreshToken(PUBLIC_ID).value();
    }

    private JwtProperties properties() {
        return new JwtProperties(SECRET, Duration.ofMinutes(30), REFRESH_TTL);
    }
}