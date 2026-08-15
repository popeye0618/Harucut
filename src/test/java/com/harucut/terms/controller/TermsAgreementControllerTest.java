package com.harucut.terms.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.config.SecurityConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.terms.dto.AgreementItem;
import com.harucut.terms.dto.TermsAgreementStatusResponse;
import com.harucut.terms.enums.TermsAgreementStatus;
import com.harucut.terms.service.TermsService;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(TermsAgreementController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("TermsAgreementController")
class TermsAgreementControllerTest extends SecurityBeansMockSupport {

    private static final String STATUS_URI = "/api/auth/terms/consents/me";
    private static final String AGREE_URI = "/api/auth/terms/consents";

    private static final String PUBLIC_ID = "AbCdEf12Gh";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private TermsService termsService;

    @Nested
    @DisplayName("GET /api/auth/terms/consents/me")
    class GetMyStatus {

        @Test
        @DisplayName("인증된 사용자에게 200과 상태 목록을 반환한다")
        void returnsStatuses() {
            given(termsService.getMyAgreementStatus(PUBLIC_ID)).willReturn(List.of(
                    new TermsAgreementStatusResponse("tos", "이용약관", true,
                            TermsAgreementStatus.NEEDS_RECONSENT, 1, 2)));

            assertThat(mockMvc.get().uri(STATUS_URI).cookie(accessCookie(PUBLIC_ID)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data[0].status", s -> assertThat(s).isEqualTo("NEEDS_RECONSENT"))
                    .hasPathSatisfying("$.data[0].agreedVersion", v -> assertThat(v).isEqualTo(1))
                    .hasPathSatisfying("$.data[0].latestVersion", v -> assertThat(v).isEqualTo(2));
        }

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010이고 서비스가 호출되지 않는다")
        void unauthenticated() {
            assertThat(mockMvc.get().uri(STATUS_URI))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

            then(termsService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("POST /api/auth/terms/consents")
    class Agree {

        @Test
        @DisplayName("정상 요청은 200이고 항목들이 그대로 서비스에 전달된다")
        void success() {
            assertThat(post("""
                    [{"code":"tos","agreed":true},{"code":"marketing","agreed":false}]"""))
                    .hasStatusOk();

            ArgumentCaptor<List<AgreementItem>> captor = ArgumentCaptor.captor();
            then(termsService).should().agree(eq(PUBLIC_ID), captor.capture());
            assertThat(captor.getValue()).containsExactly(
                    new AgreementItem("tos", true),
                    new AgreementItem("marketing", false));
        }

        @Test
        @DisplayName("code가 빈 문자열이면 400이고 서비스가 호출되지 않는다")
        void blankCode() {
            assertThat(post("""
                    [{"code":"","agreed":true}]"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-002"));

            then(termsService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("agreed가 없으면 400이고 서비스가 호출되지 않는다")
        void missingAgreed() {
            assertThat(post("""
                    [{"code":"tos"}]"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-002"));

            then(termsService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("배열이 아니라 객체를 보내면 GEN-006이다")
        void objectInsteadOfArray() {
            assertThat(post("""
                    {"code":"tos","agreed":true}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-006"));

            then(termsService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("본문이 없으면 GEN-006이고 서비스가 호출되지 않는다")
        void missingBody() {
            assertThat(mockMvc.post().uri(AGREE_URI)
                    .cookie(accessCookie(PUBLIC_ID))
                    .contentType(MediaType.APPLICATION_JSON))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-006"));

            then(termsService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010이고 서비스가 호출되지 않는다")
        void unauthenticated() {
            assertThat(mockMvc.post().uri(AGREE_URI)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            [{"code":"tos","agreed":true}]"""))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

            then(termsService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("탈퇴 요청 상태면 403 GEN-021이고 서비스가 호출되지 않는다")
        void deletedRequested() {
            assertThat(mockMvc.post().uri(AGREE_URI)
                    .cookie(accessCookie(PUBLIC_ID, UserStatus.DELETED_REQUESTED))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            [{"code":"tos","agreed":true}]"""))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

            then(termsService).shouldHaveNoInteractions();
        }

        private MockMvcTester.MockMvcRequestBuilder post(String json) {
            return mockMvc.post().uri(AGREE_URI)
                    .cookie(accessCookie(PUBLIC_ID))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json);
        }
    }

    private Cookie accessCookie(String publicId) {
        return accessCookie(publicId, UserStatus.ACTIVE);
    }

    private Cookie accessCookie(String publicId, UserStatus status) {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(publicId, UserRole.ROLE_USER, status).value());
    }
}
