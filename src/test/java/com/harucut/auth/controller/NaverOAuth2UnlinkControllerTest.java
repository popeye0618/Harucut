package com.harucut.auth.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.oauth2.unlink.NaverOAuth2UnlinkService;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.config.SecurityConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(NaverOAuth2UnlinkController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, CookieManager.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("NaverOAuth2UnlinkController")
class NaverOAuth2UnlinkControllerTest extends SecurityBeansMockSupport {

    private static final String UNLINK_URI = "/api/oauth2/unlink/naver";
    private static final String BODY = """
            {"clientId":"cid","encryptUniqueId":"enc","timestamp":"1755450000","signature":"sig"}""";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private NaverOAuth2UnlinkService naverOAuth2UnlinkService;

    // 네이버 서버가 부르는 웹훅이라 쿠키 없이 호출한다 — permitAll 검증을 겸한다
    @Test
    @DisplayName("정상 통보는 204이고 바디가 그대로 서비스로 간다")
    void unlinkReturns204() {
        MvcTestResult result = mockMvc.post().uri(UNLINK_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT).hasBodyTextEqualTo("");

        ArgumentCaptor<NaverUnlinkRequest> captor = ArgumentCaptor.forClass(NaverUnlinkRequest.class);
        then(naverOAuth2UnlinkService).should().unlink(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new NaverUnlinkRequest("cid", "enc", "1755450000", "sig"));
    }

    @Test
    @DisplayName("서명 검증 실패는 500 AUTH-091이다")
    void badSignatureReturns500() {
        willThrow(new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED))
                .given(naverOAuth2UnlinkService).unlink(any(NaverUnlinkRequest.class));

        MvcTestResult result = mockMvc.post().uri(UNLINK_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-091"));
    }

    @Test
    @DisplayName("해당 사용자가 없으면 404 GEN-031이다")
    void unknownUserReturns404() {
        willThrow(new BusinessException(GlobalErrorCode.NOT_FOUND))
                .given(naverOAuth2UnlinkService).unlink(any(NaverUnlinkRequest.class));

        MvcTestResult result = mockMvc.post().uri(UNLINK_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY)
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-031"));
    }
}
