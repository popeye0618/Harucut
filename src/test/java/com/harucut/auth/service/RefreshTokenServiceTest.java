package com.harucut.auth.service;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.cookie.CookieProperties;
import com.harucut.auth.dto.AuthTokenCookies;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final String SECRET =
            "test-secret-key-must-be-at-least-256-bits-for-hs256-algorithm-aa";
    private static final String PUBLIC_ID = "test-public-id";
    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    private static final Clock CLOCK = Clock.fixed(
            FixedClockConfig.FIXED_NOW.atZone(FixedClockConfig.ZONE).toInstant(),
            FixedClockConfig.ZONE);

    @Mock
    private RefreshTokenRepository repository;

    @Mock
    private UserRepository userRepository;

    private JwtTokenService jwtTokenService;
    private RefreshTokenService refreshTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(properties(), CLOCK);
        refreshTokenService = new RefreshTokenService(
                repository,
                userRepository,
                jwtTokenService,
                new CookieManager(new CookieProperties("localhost", false, "Lax")));
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("저장소에 그대로 위임한다")
        void delegates() {
            IssuedToken token = new IssuedToken("refresh-value", REFRESH_TTL);

            refreshTokenService.save(PUBLIC_ID, token);

            then(repository).should().save(PUBLIC_ID, token);
        }
    }

    @Nested
    @DisplayName("reissue")
    class Reissue {

        @Test
        @DisplayName("회전에 성공하면 새 토큰을 쿠키로 내려준다")
        void rotates() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Rotated("new-refresh"));
            givenStoredUser(UserStatus.ACTIVE, UserRole.ROLE_USER);

            AuthTokenCookies cookies = refreshTokenService.reissue(presented);

            assertThat(cookies.refreshTokenCookie().getValue()).isEqualTo("new-refresh");
            assertThat(jwtTokenService.parse(cookies.accessTokenCookie().getValue()).type())
                    .isEqualTo(TokenType.ACCESS);
        }

        @Test
        @DisplayName("스크립트가 만들어 둔 후보 토큰을 그대로 넘긴다")
        void passesCandidateToScript() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Rotated("new-refresh"));
            givenStoredUser(UserStatus.ACTIVE, UserRole.ROLE_USER);

            refreshTokenService.reissue(presented);

            ArgumentCaptor<IssuedToken> candidate = ArgumentCaptor.forClass(IssuedToken.class);
            then(repository).should().rotate(eq(PUBLIC_ID), eq(presented), candidate.capture());

            assertThat(candidate.getValue().value()).isNotEqualTo(presented);
            assertThat(candidate.getValue().ttl()).isEqualTo(REFRESH_TTL);
            assertThat(jwtTokenService.parse(candidate.getValue().value()).type())
                    .isEqualTo(TokenType.REFRESH);
        }

        @Test
        @DisplayName("grace 창 안의 동시 요청에는 회전된 현재 토큰을 그대로 준다")
        void gracePeriodReturnsCurrentToken() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Graced("current-refresh"));
            givenStoredUser(UserStatus.ACTIVE, UserRole.ROLE_USER);

            AuthTokenCookies cookies = refreshTokenService.reissue(presented);

            assertThat(cookies.refreshTokenCookie().getValue()).isEqualTo("current-refresh");
        }

        @Test
        @DisplayName("grace로 통과해도 access 토큰은 새로 발급한다")
        void gracePeriodStillIssuesAccessToken() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Graced("current-refresh"));
            givenStoredUser(UserStatus.ACTIVE, UserRole.ROLE_USER);

            AuthTokenCookies cookies = refreshTokenService.reissue(presented);

            assertThat(jwtTokenService.parse(cookies.accessTokenCookie().getValue()).type())
                    .isEqualTo(TokenType.ACCESS);
        }

        /*
         * 이 테스트가 "차단이 최대 30분 안에 반영된다"의 유일한 증거다.
         * 재발급이 DB를 다시 읽어 최신 status를 새 토큰에 싣는지를 본다.
         */
        @Test
        @DisplayName("재발급된 access 토큰에는 DB의 최신 role/status가 실린다")
        void putsFreshAuthorizationClaimsOnNewAccessToken() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Rotated("new-refresh"));
            givenStoredUser(UserStatus.BLOCKED, UserRole.ROLE_ADMIN);

            AuthTokenCookies cookies = refreshTokenService.reissue(presented);

            JwtClaims claims = jwtTokenService.parse(cookies.accessTokenCookie().getValue());
            assertThat(claims.status()).isEqualTo(UserStatus.BLOCKED);
            assertThat(claims.role()).isEqualTo(UserRole.ROLE_ADMIN);
        }

        @Test
        @DisplayName("회전이 거부되면 사용자를 조회하지 않는다")
        void skipsUserLookupWhenRotationRejected() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.ReuseDetected());

            assertThatThrownBy(() -> refreshTokenService.reissue(presented))
                    .isInstanceOf(BusinessException.class);

            then(userRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("회전에 성공해도 사용자가 없으면 AUTH-011이다")
        void rejectsWhenUserGone() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.Rotated("new-refresh"));
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> refreshTokenService.reissue(presented))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("세션이 없으면 AUTH-011을 던진다")
        void noSession() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.NoSession());

            assertThatThrownBy(() -> refreshTokenService.reissue(presented))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("재사용으로 판정되면 AUTH-011을 던진다")
        void reuseDetected() {
            String presented = oldRefreshToken();
            givenRotation(presented, new RotationResult.ReuseDetected());

            assertThatThrownBy(() -> refreshTokenService.reissue(presented))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("access 토큰을 넣으면 저장소를 보지도 않고 AUTH-011을 던진다")
        void accessTokenRejected() {
            String accessToken = jwtTokenService
                    .createAccessToken(PUBLIC_ID, UserRole.ROLE_USER, UserStatus.ACTIVE).value();

            assertThatThrownBy(() -> refreshTokenService.reissue(accessToken))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);

            then(repository).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("revoke")
    class Revoke {

        @Test
        @DisplayName("저장소에 삭제를 위임한다")
        void delegates() {
            refreshTokenService.revoke(PUBLIC_ID);

            then(repository).should().delete(PUBLIC_ID);
        }

        @Test
        @DisplayName("삭제가 실패하면 예외를 그대로 던진다")
        void propagatesRedisFailure() {
            willThrow(new RedisConnectionFailureException("down"))
                    .given(repository).delete(PUBLIC_ID);

            assertThatThrownBy(() -> refreshTokenService.revoke(PUBLIC_ID))
                    .isInstanceOf(RedisConnectionFailureException.class);
        }
    }

    private void givenRotation(String presented, RotationResult result) {
        given(repository.rotate(eq(PUBLIC_ID), eq(presented), any(IssuedToken.class)))
                .willReturn(result);
    }

    /** 회전에 성공한 요청만 사용자 조회까지 간다. */
    private User givenStoredUser(UserStatus status, UserRole role) {
        User user = UserFixtures.localUser("user@harucut.com", "encoded", status, role);
        ReflectionTestUtils.setField(user, "publicId", PUBLIC_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
        return user;
    }

    private String oldRefreshToken() {
        return new JwtTokenService(properties(), Clock.offset(CLOCK, Duration.ofMinutes(-1)))
                .createRefreshToken(PUBLIC_ID).value();
    }

    private JwtProperties properties() {
        return new JwtProperties(SECRET, Duration.ofMinutes(30), REFRESH_TTL);
    }
}
