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
        NoticeController.class, UserApiFixtureController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("보안 필터 체인")
class SecurityFilterChainTest {

    private static final String PROTECTED_URI = "/api/auth/status";
    private static final String ADMIN_URI = "/api/admin/notices";
    private static final String USER_URI = "/fixture/user";
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
    private CustomUserDetailsService userDetailsService;

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
        @DisplayName("DELETED_REQUESTED 사용자가 관리자 API를 호출하면 403 GEN-021을 반환한다")
        void deletedRequestedCannotCallAdminApi() {
            User user = user(UserStatus.DELETED_REQUESTED, UserRole.ROLE_USER);

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
    @DisplayName("일반 사용자 API (hasRole('USER'))")
    class UserApi {

        @Test
        @DisplayName("ACTIVE 사용자는 통과한다")
        void activeUserPasses() {
            User user = activeUser();

            assertThat(mockMvc.get().uri(USER_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatusOk();
        }

        @Test
        @DisplayName("DELETED_REQUESTED 사용자는 403 GEN-021을 받는다")
        void deletedRequestedIsRejected() {
            User user = user(UserStatus.DELETED_REQUESTED, UserRole.ROLE_USER);

            assertThat(mockMvc.get().uri(USER_URI).cookie(accessCookie(accessToken(user))))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));
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
    }

    private User activeUser() {
        return user(UserStatus.ACTIVE, UserRole.ROLE_USER);
    }

    private User user(UserStatus status, UserRole role) {
        User user = UserFixtures.localUser("user@harucut.com", "encoded", status, role);
        given(userDetailsService.loadUserByPublicId(user.getPublicId()))
                .willReturn(new CustomUserPrincipal(user));
        return user;
    }

    private String accessToken(User user) {
        return jwtTokenService.createAccessToken(user.getPublicId()).value();
    }

    private String expiredAccessToken() {
        Clock past = Clock.offset(clock,
                jwtProperties.accessExpiration().plus(Duration.ofMinutes(1)).negated());

        IssuedToken token = new JwtTokenService(jwtProperties, past).createAccessToken("expired-user");
        return token.value();
    }

    private String forgedAccessToken() {
        String token = jwtTokenService.createAccessToken("forged-user").value();
        return token.substring(0, token.lastIndexOf('.')) + ".Zm9yZ2Vk";
    }

    private Cookie accessCookie(String value) {
        return new Cookie("accessToken", value);
    }
}
