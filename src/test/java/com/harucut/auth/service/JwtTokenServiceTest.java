package com.harucut.auth.service;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import com.harucut.support.FixedClockConfig;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class JwtTokenServiceTest {

    private static final ZoneId ZONE = FixedClockConfig.ZONE;
    private static final Instant BASE = FixedClockConfig.FIXED_NOW.atZone(ZONE).toInstant();

    private static final String SECRET =
            "test-secret-key-must-be-at-least-256-bits-for-hs256-algorithm-aa";
    private static final String OTHER_SECRET =
            "another-secret-key-must-be-at-least-256-bits-for-hs256-algo-bbbb";

    private static final Duration ACCESS_EXPIRATION = Duration.ofMinutes(30);
    private static final Duration REFRESH_EXPIRATION = Duration.ofDays(14);
    private static final String PUBLIC_ID = "AbCdEf123456";
    private static final UserRole ROLE = UserRole.ROLE_USER;
    private static final UserStatus STATUS = UserStatus.ACTIVE;

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = serviceAt(BASE);
    }

    @Nested
    @DisplayName("토큰 발급")
    class Create {

        @Test
        @DisplayName("발급한 토큰을 파싱하면 원래 publicId가 나온다")
        void roundTripsPublicId() {
            IssuedToken token = jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS);

            assertThat(jwtTokenService.parse(token.value()).publicId()).isEqualTo(PUBLIC_ID);
        }

        @Test
        @DisplayName("access는 ACCESS, refresh는 REFRESH 타입으로 발급된다")
        void marksTokenType() {
            String access = jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS).value();
            String refresh = jwtTokenService.createRefreshToken(PUBLIC_ID).value();

            assertThat(jwtTokenService.parse(access).type()).isEqualTo(TokenType.ACCESS);
            assertThat(jwtTokenService.parse(refresh).type()).isEqualTo(TokenType.REFRESH);
        }

        @Test
        @DisplayName("발급 결과의 ttl이 설정값과 같다")
        void exposesConfiguredTtl() {
            IssuedToken access = jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS);
            IssuedToken refresh = jwtTokenService.createRefreshToken(PUBLIC_ID);

            assertThat(access.ttl()).isEqualTo(ACCESS_EXPIRATION);
            assertThat(refresh.ttl()).isEqualTo(REFRESH_EXPIRATION);
        }

        @Test
        @DisplayName("클레임에 sub, iss, type, role, status가 담기고 exp - iat가 만료 시간과 같다")
        void writesExpectedClaims() {
            Claims claims = rawClaims(jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS).value());

            assertThat(claims.getSubject()).isEqualTo(PUBLIC_ID);
            assertThat(claims.getIssuer()).isEqualTo("Harucut");
            assertThat(claims.get("type", String.class)).isEqualTo("ACCESS");
            assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
            assertThat(claims.get("status", String.class)).isEqualTo("ACTIVE");
            assertThat(claims.getIssuedAt()).isEqualTo(Date.from(BASE));
            assertThat(claims.getExpiration().getTime() - claims.getIssuedAt().getTime())
                    .isEqualTo(ACCESS_EXPIRATION.toMillis());
        }

        @Test
        @DisplayName("access 토큰의 role/status는 넣은 그대로 돌아온다")
        void roundTripsAuthorizationClaims() {
            IssuedToken token = jwtTokenService.createAccessToken(
                    PUBLIC_ID, UserRole.ROLE_ADMIN, UserStatus.DELETED_REQUESTED);

            JwtClaims claims = jwtTokenService.parse(token.value());

            assertThat(claims.role()).isEqualTo(UserRole.ROLE_ADMIN);
            assertThat(claims.status()).isEqualTo(UserStatus.DELETED_REQUESTED);
        }

        @Test
        @DisplayName("refresh 토큰에는 인가 정보를 싣지 않는다")
        void refreshCarriesNoAuthorizationClaims() {
            String refresh = jwtTokenService.createRefreshToken(PUBLIC_ID).value();

            Claims raw = rawClaims(refresh);
            assertThat(raw.get("role")).isNull();
            assertThat(raw.get("status")).isNull();

            JwtClaims claims = jwtTokenService.parse(refresh);
            assertThat(claims.role()).isNull();
            assertThat(claims.status()).isNull();
        }
    }

    @Nested
    @DisplayName("토큰 파싱")
    class Parse {

        @Test
        @DisplayName("만료 1초 전에는 통과한다")
        void acceptsTokenJustBeforeExpiry() {
            String token = jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS).value();
            JwtTokenService justBefore = serviceAt(BASE.plus(ACCESS_EXPIRATION).minusSeconds(1));

            assertThat(justBefore.parse(token).publicId()).isEqualTo(PUBLIC_ID);
        }

        @Test
        @DisplayName("만료 1초 후에는 AUTH-012다")
        void rejectsExpiredToken() {
            String token = jwtTokenService.createAccessToken(PUBLIC_ID, ROLE, STATUS).value();
            JwtTokenService justAfter = serviceAt(BASE.plus(ACCESS_EXPIRATION).plusSeconds(1));

            assertThatThrownBy(() -> justAfter.parse(token))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EXPIRED_TOKEN);
        }

        @Test
        @DisplayName("다른 키로 서명된 토큰은 AUTH-011이다")
        void rejectsTokenSignedWithAnotherKey() {
            String forged = new JwtTokenService(
                    new JwtProperties(OTHER_SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION),
                    Clock.fixed(BASE, ZONE)
            ).createAccessToken(PUBLIC_ID, ROLE, STATUS).value();

            assertThatThrownBy(() -> jwtTokenService.parse(forged))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @ParameterizedTest(name = "[{index}] \"{0}\"")
        @NullAndEmptySource
        @ValueSource(strings = {"garbage", "a.b.c", "eyJhbGciOiJIUzI1NiJ9"})
        @DisplayName("JWT가 아닌 문자열은 AUTH-011이다")
        void rejectsMalformedToken(String token) {
            assertThatThrownBy(() -> jwtTokenService.parse(token))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("서명은 맞지만 type 클레임이 없으면 AUTH-011이다")
        void rejectsTokenWithoutTypeClaim() {
            String token = signedWithoutType(null);

            assertThatThrownBy(() -> jwtTokenService.parse(token))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("모르는 type 값이면 AUTH-011이다")
        void rejectsUnknownTokenType() {
            String token = signedWithoutType("BANANA");

            assertThatThrownBy(() -> jwtTokenService.parse(token))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        /*
         * 인가 정보가 토큰 안으로 들어왔으므로, 클레임이 없거나 모르는 값일 때
         * 기본값으로 떨어지면 클레임 하나 지운 토큰으로 권한을 얻는다. 반드시 거부여야 한다.
         */
        @ParameterizedTest(name = "[{index}] role={0}, status={1}")
        @CsvSource(nullValues = "null", value = {
                "null,         ACTIVE",
                "ROLE_USER,    null",
                "null,         null",
                "ROLE_MASTER,  ACTIVE",
                "ROLE_USER,    ZOMBIE"
        })
        @DisplayName("access 토큰의 role/status가 없거나 모르는 값이면 AUTH-011이다")
        void rejectsAccessTokenWithBadAuthorizationClaims(String role, String status) {
            String token = signedAccess("ACCESS", role, status);

            assertThatThrownBy(() -> jwtTokenService.parse(token))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("refresh 토큰은 role/status가 없어도 통과한다")
        void acceptsRefreshTokenWithoutAuthorizationClaims() {
            String token = signedAccess("REFRESH", null, null);

            assertThat(jwtTokenService.parse(token).publicId()).isEqualTo(PUBLIC_ID);
        }
    }

    private static JwtTokenService serviceAt(Instant instant) {
        return new JwtTokenService(
                new JwtProperties(SECRET, ACCESS_EXPIRATION, REFRESH_EXPIRATION),
                Clock.fixed(instant, ZONE)
        );
    }

    private static SecretKey secretKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private static Claims rawClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .clock(() -> Date.from(BASE))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private static String signedWithoutType(String type) {
        return signedAccess(type, ROLE.name(), STATUS.name());
    }

    private static String signedAccess(String type, String role, String status) {
        JwtBuilder builder = Jwts.builder()
                .subject(PUBLIC_ID)
                .issuer("Harucut")
                .issuedAt(Date.from(BASE))
                .expiration(Date.from(BASE.plus(ACCESS_EXPIRATION)));

        if (type != null) {
            builder.claim("type", type);
        }
        if (role != null) {
            builder.claim("role", role);
        }
        if (status != null) {
            builder.claim("status", status);
        }
        return builder.signWith(secretKey()).compact();
    }
}