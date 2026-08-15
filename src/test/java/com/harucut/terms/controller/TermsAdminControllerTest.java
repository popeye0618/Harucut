package com.harucut.terms.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.PageResponse;
import com.harucut.config.SecurityConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.terms.dto.TermsAdminResponse;
import com.harucut.terms.dto.TermsAgreementHistoryResponse;
import com.harucut.terms.exception.TermsErrorCode;
import com.harucut.terms.service.TermsAdminService;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(TermsAdminController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("TermsAdminController")
class TermsAdminControllerTest extends SecurityBeansMockSupport {

    private static final String TERMS_URI = "/api/admin/terms";

    private static final String ADMIN_PUBLIC_ID = "AdminAb12Cd";

    private static final String VALID_CREATE_BODY = """
            {"code":"tos","title":"이용약관","required":true,"content":"본문"}""";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private TermsAdminService termsAdminService;

    @Nested
    @DisplayName("권한")
    class Authorization {

        @Test
        @DisplayName("USER 권한이면 403 GEN-021이고 서비스가 호출되지 않는다")
        void userRoleForbidden() {
            assertThat(mockMvc.post().uri(TERMS_URI)
                    .cookie(cookie(UserRole.ROLE_USER))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_CREATE_BODY))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

            then(termsAdminService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("토큰이 없으면 401 AUTH-010이다")
        void unauthenticated() {
            assertThat(mockMvc.get().uri(TERMS_URI))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

            then(termsAdminService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("POST /api/admin/terms")
    class CreateTerms {

        @Test
        @DisplayName("정상 요청은 200이고 필드가 그대로 서비스에 전달된다")
        void success() {
            assertThat(adminPost(TERMS_URI, VALID_CREATE_BODY)).hasStatusOk();

            then(termsAdminService).should().createTerms("tos", "이용약관", true, "본문");
        }

        @Test
        @DisplayName("대문자 코드(TOS)는 GEN-003이고 위반 필드가 code다")
        void upperCaseCode() {
            assertThat(adminPost(TERMS_URI, """
                    {"code":"TOS","title":"이용약관","required":true,"content":"본문"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("code"));

            then(termsAdminService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("공백이 든 코드(to s)는 GEN-003이다")
        void codeWithSpace() {
            assertThat(adminPost(TERMS_URI, """
                    {"code":"to s","title":"이용약관","required":true,"content":"본문"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

            then(termsAdminService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("51자 코드는 GEN-003이다")
        void codeTooLong() {
            assertThat(adminPost(TERMS_URI, """
                    {"code":"%s","title":"이용약관","required":true,"content":"본문"}""".formatted("a".repeat(51))))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

            then(termsAdminService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("required가 없으면 GEN-003이다")
        void missingRequired() {
            assertThat(adminPost(TERMS_URI, """
                    {"code":"tos","title":"이용약관","content":"본문"}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"))
                    .hasPathSatisfying("$.data[0].field", f -> assertThat(f).isEqualTo("required"));

            then(termsAdminService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("중복 코드는 409 TERMS-002다")
        void duplicateCode() {
            willThrow(new BusinessException(TermsErrorCode.TERMS_CODE_DUPLICATED))
                    .given(termsAdminService).createTerms("tos", "이용약관", true, "본문");

            assertThat(adminPost(TERMS_URI, VALID_CREATE_BODY))
                    .hasStatus(HttpStatus.CONFLICT)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("TERMS-002"));
        }
    }

    @Nested
    @DisplayName("POST /api/admin/terms/{termsId}/versions")
    class ReviseTerms {

        @Test
        @DisplayName("정상 개정은 200이고 termsId와 본문이 전달된다")
        void success() {
            assertThat(adminPost(TERMS_URI + "/1/versions", """
                    {"content":"개정 본문"}"""))
                    .hasStatusOk();

            then(termsAdminService).should().reviseTerms(1L, "개정 본문");
        }

        @Test
        @DisplayName("content가 비어 있으면 GEN-003이다")
        void blankContent() {
            assertThat(adminPost(TERMS_URI + "/1/versions", """
                    {"content":""}"""))
                    .hasStatus(HttpStatus.BAD_REQUEST)
                    .bodyJson()
                    .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

            then(termsAdminService).shouldHaveNoInteractions();
        }
    }

    @Nested
    @DisplayName("GET /api/admin/terms")
    class ListAllTerms {

        @Test
        @DisplayName("비활성 포함 전체 목록을 200으로 반환한다")
        void returnsAll() {
            given(termsAdminService.listAllTerms()).willReturn(List.of(
                    new TermsAdminResponse(3L, "marketing", "마케팅 수신 동의", false, false, 1, "본문")));

            assertThat(mockMvc.get().uri(TERMS_URI).cookie(cookie(UserRole.ROLE_ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data[0].termsId", id -> assertThat(id).isEqualTo(3))
                    .hasPathSatisfying("$.data[0].active", a -> assertThat(a).isEqualTo(false));
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/terms/{termsId}")
    class DeactivateTerms {

        @Test
        @DisplayName("비활성화는 200이고 termsId가 전달된다")
        void success() {
            assertThat(mockMvc.delete().uri(TERMS_URI + "/1").cookie(cookie(UserRole.ROLE_ADMIN)))
                    .hasStatusOk();

            then(termsAdminService).should().deactivateTerms(1L);
        }
    }

    @Nested
    @DisplayName("GET /api/admin/terms/consents/{userId}")
    class GetAgreementHistory {

        @Test
        @DisplayName("사용자 동의 이력을 페이지로 반환한다")
        void returnsHistoryPage() {
            given(termsAdminService.getAgreementHistory(7L, 0, 10)).willReturn(PageResponse.from(
                    new PageImpl<>(List.of(new TermsAgreementHistoryResponse(
                            "tos", 1, true, LocalDateTime.of(2026, 8, 16, 12, 0))))));

            assertThat(mockMvc.get().uri(TERMS_URI + "/consents/7")
                    .param("page", "0").param("size", "10")
                    .cookie(cookie(UserRole.ROLE_ADMIN)))
                    .hasStatusOk()
                    .bodyJson()
                    .hasPathSatisfying("$.data.content[0].code", c -> assertThat(c).isEqualTo("tos"))
                    .hasPathSatisfying("$.data.content[0].agreed", a -> assertThat(a).isEqualTo(true));
        }
    }

    private MockMvcTester.MockMvcRequestBuilder adminPost(String uri, String json) {
        return mockMvc.post().uri(uri)
                .cookie(cookie(UserRole.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    private Cookie cookie(UserRole role) {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(ADMIN_PUBLIC_ID, role, UserStatus.ACTIVE).value());
    }
}
