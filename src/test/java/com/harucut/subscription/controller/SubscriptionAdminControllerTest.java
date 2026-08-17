package com.harucut.subscription.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.common.exception.BusinessException;
import com.harucut.config.SecurityConfig;
import com.harucut.subscription.dto.SubscriptionAdminResponse;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.exception.SubscriptionErrorCode;
import com.harucut.subscription.service.SubscriptionAdminService;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(SubscriptionAdminController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("SubscriptionAdminController")
class SubscriptionAdminControllerTest extends SecurityBeansMockSupport {

    private static final String URI = "/api/admin/subscriptions/1";
    private static final String REMOVED_PATCH_URI = "/api/admin/subscriptions/1/plan";
    private static final String ADMIN_PUBLIC_ID = "AdminUser01";

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private SubscriptionAdminService subscriptionAdminService;

    @Test
    @DisplayName("관리자에게 결제한 등급과 실제 적용 등급이 나란히 직렬화된다")
    void returnsBothTiers() {
        given(subscriptionAdminService.getSubscription(1L)).willReturn(new SubscriptionAdminResponse(
                PlanTier.PLUS, PlanTier.BASIC, SubscriptionStatus.ACTIVE,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0), true));

        assertThat(mockMvc.get().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.data.planTier", p -> assertThat(p).isEqualTo("PLUS"))
                .hasPathSatisfying("$.data.effectiveTier", e -> assertThat(e).isEqualTo("BASIC"))
                .hasPathSatisfying("$.data.status", s -> assertThat(s).isEqualTo("ACTIVE"))
                .hasPathSatisfying("$.data.autoRenew", a -> assertThat(a).isEqualTo(true));
    }

    @Test
    @DisplayName("일반 사용자는 403 GEN-021이고 서비스가 호출되지 않는다")
    void forbiddenForUser() {
        assertThat(mockMvc.get().uri(URI).cookie(accessCookie(UserRole.ROLE_USER)))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

        then(subscriptionAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰이 없으면 401 AUTH-010이고 서비스가 호출되지 않는다")
    void unauthenticated() {
        assertThat(mockMvc.get().uri(URI))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("AUTH-010"));

        then(subscriptionAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("구독이 없으면 404 SUBS-004다")
    void noSubscription() {
        given(subscriptionAdminService.getSubscription(1L))
                .willThrow(new BusinessException(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION));

        assertThat(mockMvc.get().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("SUBS-004"));
    }

    @Test
    @DisplayName("요금제 강제 변경 API는 존재하지 않는다 — 404 GEN-031")
    void planChangeEndpointGone() {
        assertThat(mockMvc.patch().uri(REMOVED_PATCH_URI)
                .param("planTier", "PRO")
                .cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-031"));

        then(subscriptionAdminService).shouldHaveNoInteractions();
    }

    private Cookie accessCookie(UserRole role) {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(ADMIN_PUBLIC_ID, role, UserStatus.ACTIVE).value());
    }
}
