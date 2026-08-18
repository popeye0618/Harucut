package com.harucut.auth.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.auth.service.UserExitService;
import com.harucut.config.SecurityConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@WebMvcTest(UserExitController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, CookieManager.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("UserExitController")
class UserExitControllerTest extends SecurityBeansMockSupport {

    private static final String EXIT_URI = "/api/harucut/exit";
    private static final String REACTIVATE_URI = "/api/harucut/reactivate";
    private static final String PUBLIC_ID = "AbCdEf12Gh";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private UserExitService userExitService;

    @Test
    @DisplayName("탈퇴 요청은 200과 만료 쿠키 2개를 주고 principal의 publicId가 서비스로 간다")
    void exitExpiresCookies() {
        MvcTestResult result = mockMvc.delete().uri(EXIT_URI)
                .cookie(accessCookie(UserStatus.ACTIVE))
                .exchange();

        assertThat(result).hasStatusOk();
        assertExpiredCookies(result);
        then(userExitService).should().requestExit(PUBLIC_ID);
    }

    @Test
    @DisplayName("토큰 없이 탈퇴 요청하면 401이고 서비스가 호출되지 않는다")
    void exitUnauthenticated() {
        assertThat(mockMvc.delete().uri(EXIT_URI))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        then(userExitService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("DELETED_REQUESTED 사용자의 복구는 200이다")
    void reactivateAsDeleteRequested() {
        assertThat(mockMvc.post().uri(REACTIVATE_URI)
                .cookie(accessCookie(UserStatus.DELETED_REQUESTED)))
                .hasStatusOk();

        then(userExitService).should().reActivate(PUBLIC_ID);
    }

    @Test
    @DisplayName("ACTIVE 사용자의 복구는 403 GEN-021이다")
    void reactivateAsActive() {
        assertThat(mockMvc.post().uri(REACTIVATE_URI)
                .cookie(accessCookie(UserStatus.ACTIVE)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

        then(userExitService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰 없이 복구하면 401이다")
    void reactivateUnauthenticated() {
        assertThat(mockMvc.post().uri(REACTIVATE_URI))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        then(userExitService).shouldHaveNoInteractions();
    }

    private Cookie accessCookie(UserStatus status) {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(PUBLIC_ID, UserRole.ROLE_USER, status).value());
    }

    private void assertExpiredCookies(MvcTestResult result) {
        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).hasSize(2);
        assertThat(cookies).allSatisfy(c -> assertThat(c).contains("Max-Age=0", "HttpOnly", "Path=/"));
    }
}