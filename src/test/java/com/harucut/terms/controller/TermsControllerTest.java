package com.harucut.terms.controller;

import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.config.SecurityConfig;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.terms.dto.TermsResponse;
import com.harucut.terms.service.TermsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@WebMvcTest(TermsController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("TermsController")
class TermsControllerTest extends SecurityBeansMockSupport {

    private static final String TERMS_URI = "/api/terms";

    @Autowired
    private MockMvcTester mockMvc;

    @MockitoBean
    private TermsService termsService;

    @Test
    @DisplayName("토큰 없이 호출해도 200과 활성 약관 목록을 반환한다")
    void publicAccessWithoutToken() {
        given(termsService.getActiveTerms())
                .willReturn(List.of(new TermsResponse("tos", "이용약관", true, 2, "본문")));

        assertThat(mockMvc.get().uri(TERMS_URI))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-000"))
                .hasPathSatisfying("$.data[0].code", c -> assertThat(c).isEqualTo("tos"))
                .hasPathSatisfying("$.data[0].version", v -> assertThat(v).isEqualTo(2));
    }
}
