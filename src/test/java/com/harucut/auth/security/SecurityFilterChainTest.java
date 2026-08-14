package com.harucut.auth.security;

import com.harucut.auth.controller.AuthStatusController;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.config.SecurityConfig;
import com.harucut.notice.controller.NoticeAdminController;
import com.harucut.notice.controller.NoticeController;
import com.harucut.notice.service.NoticeAdminService;
import com.harucut.notice.service.NoticeService;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest({AuthStatusController.class, NoticeAdminController.class,
        NoticeController.class, PlainApiFixtureController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("보안 필터 체인")
class SecurityFilterChainTest {

    private static final String PROTECTED_URI = "/api/auth/status";
    private static final String ADMIN_URI = "/api/admin/notices";
    private static final String PLAIN_URI = "/fixture/plain";
    private static final String PUBLIC_URI = "/api/notices";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private Clock clock;

    @MockitoBean
    private NoticeAdminService noticeAdminService;

    @MockitoBean
    private NoticeService noticeService;

    @Nested
    @DisplayName("토큰 검증")
    class TokenVerification {

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010을 공통 봉투로 반환한다")
        void noToken() {
            assertThat(mockMvc.get().uri(PROTECTED_URI))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));
        }

        @Test
        @DisplayName("만료된 토큰이면 401 AUTH-012를 반환한다")
        void expiredToken() {
            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(expiredAccessToken())))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-012"));
        }

        @Test
        @DisplayName("서명이 위조된 토큰이면 401 AUTH-011을 반환한다")
        void forgedToken() {
            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(forgedAccessToken())))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-011"));
        }

        @Test
        @DisplayName("리프레시 토큰을 액세스 토큰 자리에 넣으면 401 AUTH-011을 반환한다")
        void refreshTokenRejected() {
            User user = activeUser();
            String refresh = jwtTokenService.createRefreshToken(user.getPublicId()).value();

            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(refresh)))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-011"));
        }

        @Test
        @DisplayName("유효한 토큰이면 200과 사용자 상태를 반환한다")
        void validToken() {
            User user = activeUser();

            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data.userStatus", s -> assertThat(s).isEqualTo("ACTIVE"));
        }
    }

    @Nested
    @DisplayName("토큰 위치")
    class TokenLocation {

        @Test
        @DisplayName("Authorization 헤더만으로도 통과한다")
        void headerFallback() {
            User user = activeUser();

            assertThat(mockMvc.get().uri(PROTECTED_URI)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken(user)))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("쿠키와 헤더가 둘 다 있으면 쿠키를 쓴다")
        void cookieWins() {
            User user = activeUser();

            assertThat(mockMvc.get().uri(PROTECTED_URI)
                    .cookie(accessCookie(accessToken(user)))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + forgedAccessToken()))
                    .hasStatusOk();
        }
    }

    @Nested
    @DisplayName("@PreAuthorize 인가")
    class MethodSecurity {

        @Test
        @DisplayName("ROLE_USER가 관리자 API를 호출하면 403 GEN-021을 반환한다")
        void userCannotCallAdminApi() {
            User user = activeUser();

            assertThat(mockMvc.get().uri(ADMIN_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));
        }

        @Test
        @DisplayName("ROLE_ADMIN은 관리자 API를 통과한다")
        void adminPasses() {
            User user = user(UserStatus.ACTIVE, UserRole.ROLE_ADMIN);

            assertThat(mockMvc.get().uri(ADMIN_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatusOk();
        }
    }

    @Nested
    @DisplayName("필터 레벨 인가 (anyRequest)")
    class RequestLevelAuthorization {

        @Test
        @DisplayName("ACTIVE 사용자는 통과한다")
        void activePasses() {
            assertThat(mockMvc.get().uri(PLAIN_URI).cookie(accessCookie(accessToken(activeUser()))))
                    .hasStatusOk();
        }

        /*
         * @PreAuthorize 가 없으므로 AuthorizationFilter 가 던지고 AccessDeniedHandler 가 응답을 만든다.
         * GlobalExceptionHandler 는 DispatcherServlet 안에 있어 이 경로에 닿지 못한다.
         * 봉투가 나왔다면 핸들러가 동작한 것이다.
         */
        @Test
        @DisplayName("BLOCKED 사용자는 403 GEN-021을 공통 봉투로 받는다")
        void blockedIsRejectedByFilter() {
            User user = user(UserStatus.BLOCKED, UserRole.ROLE_USER);

            assertThat(mockMvc.get().uri(PLAIN_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));
        }

        @Test
        @DisplayName("DELETED_REQUESTED 사용자는 403 GEN-021을 받는다")
        void deletedRequestedIsRejectedByFilter() {
            User user = user(UserStatus.DELETED_REQUESTED, UserRole.ROLE_USER);

            assertThat(mockMvc.get().uri(PLAIN_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));
        }

        @Test
        @DisplayName("토큰이 없으면 403이 아니라 401이다")
        void anonymousGets401() {
            assertThat(mockMvc.get().uri(PLAIN_URI))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));
        }
    }

    @Nested
    @DisplayName("/api/auth/status 예외")
    class AuthStatusException {

        /*
         * 차단된 사용자가 자기 상태를 확인할 유일한 창구다.
         * anyRequest() 가 아니라 authenticated() 로 따로 연 의도적 예외.
         */
        @Test
        @DisplayName("BLOCKED 사용자도 자기 상태는 조회할 수 있다")
        void blockedCanReadOwnStatus() {
            User user = user(UserStatus.BLOCKED, UserRole.ROLE_USER);

            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data.userStatus", s -> assertThat(s).isEqualTo("BLOCKED"));
        }

        @Test
        @DisplayName("DELETED_REQUESTED 사용자도 자기 상태는 조회할 수 있다")
        void deletedRequestedCanReadOwnStatus() {
            User user = user(UserStatus.DELETED_REQUESTED, UserRole.ROLE_USER);

            assertThat(mockMvc.get().uri(PROTECTED_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data.userStatus", s -> assertThat(s).isEqualTo("DELETED_REQUESTED"));
        }
    }

    @Nested
    @DisplayName("public path")
    class PublicPath {

        @Test
        @DisplayName("토큰 없이도 200을 받는다")
        void noTokenPasses() {
            assertThat(mockMvc.get().uri(PUBLIC_URI)).hasStatusOk();
        }

        @Test
        @DisplayName("만료 토큰이 붙어 있어도 401이 아니라 200을 받는다")
        void ignoresExpiredToken() {
            assertThat(mockMvc.get().uri(PUBLIC_URI).cookie(accessCookie(expiredAccessToken())))
                    .hasStatusOk();
        }

        /*
         * /api/notices/** 에 메서드 제한을 걸지 않기로 한 결정을 고정한다(docs/00-conventions.md 3절).
         * 근거는 "도달 가능한 쓰기 핸들러가 없다"는 것이므로, GET 아닌 매핑이 생기면 이 테스트가 깨진다.
         * 그때가 인가 규칙을 다시 볼 시점이다 — 기대값만 바꾸고 넘어가지 말 것.
         */
        @Test
        @DisplayName("GET이 아닌 메서드도 permitAll이라 인가가 아니라 405로 걸린다")
        void nonGetIsPublicButUnmapped() {
            assertThat(mockMvc.post().uri(PUBLIC_URI))
                    .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-041"));
        }
    }

    private User activeUser() {
        return user(UserStatus.ACTIVE, UserRole.ROLE_USER);
    }

    private User user(UserStatus status, UserRole role) {
        return UserFixtures.localUser("user@harucut.com", "encoded", status, role);
    }

    private String accessToken(User user) {
        return jwtTokenService
                .createAccessToken(user.getPublicId(), user.getUserRole(), user.getUserStatus())
                .value();
    }

    private String expiredAccessToken() {
        Clock past = Clock.offset(clock,
                jwtProperties.accessExpiration().plus(Duration.ofMinutes(1)).negated());

        IssuedToken token = new JwtTokenService(jwtProperties, past)
                .createAccessToken("expired-user", UserRole.ROLE_USER, UserStatus.ACTIVE);
        return token.value();
    }

    private String forgedAccessToken() {
        String token = jwtTokenService
                .createAccessToken("forged-user", UserRole.ROLE_USER, UserStatus.ACTIVE).value();
        return token.substring(0, token.lastIndexOf('.')) + ".Zm9yZ2Vk";
    }

    private Cookie accessCookie(String value) {
        return new Cookie("accessToken", value);
    }
}
