package com.harucut.coupon.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.common.exception.BusinessException;
import com.harucut.config.SecurityConfig;
import com.harucut.coupon.dto.CouponAdminResponse;
import com.harucut.coupon.dto.CouponCreateRequest;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.service.CouponAdminService;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import com.harucut.support.SecurityBeansMockSupport;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.servlet.http.Cookie;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@WebMvcTest(CouponAdminController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("CouponAdminController")
class CouponAdminControllerTest extends SecurityBeansMockSupport {

    private static final String URI = "/api/admin/coupons";
    private static final String ADMIN_PUBLIC_ID = "AdminUser01";
    private static final String VALID_BODY = """
            {
              "name": "가입 축하 PRO",
              "code": "WELCOME-PRO",
              "grantTier": "PRO",
              "maxRedemptions": 100,
              "validUntil": "2026-12-31T23:59:59"
            }
            """;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CouponAdminService couponAdminService;

    @Test
    @DisplayName("생성 본문이 DTO로 매핑되어 서비스로 간다")
    void createMapsBody() {
        assertThat(mockMvc.post().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatusOk();

        ArgumentCaptor<CouponCreateRequest> captor = ArgumentCaptor.forClass(CouponCreateRequest.class);
        then(couponAdminService).should().create(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new CouponCreateRequest(
                "가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO, 100,
                LocalDateTime.of(2026, 12, 31, 23, 59, 59)));
    }

    @Test
    @DisplayName("maxRedemptions가 0이면 400 GEN-003이고 서비스가 호출되지 않는다")
    void zeroMaxRedemptions() {
        String body = """
                {
                  "name": "가입 축하 PRO",
                  "code": "WELCOME-PRO",
                  "grantTier": "PRO",
                  "maxRedemptions": 0
                }
                """;

        assertThat(mockMvc.post().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

        then(couponAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("중복 코드는 HTTP 409 COUPON-002로 나간다")
    void duplicateCodeIs409() {
        willThrow(new BusinessException(CouponErrorCode.COUPON_CODE_DUPLICATED))
                .given(couponAdminService).create(any(CouponCreateRequest.class));

        assertThat(mockMvc.post().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("COUPON-002"));
    }

    @Test
    @DisplayName("일반 사용자는 403 GEN-021이고 서비스가 호출되지 않는다")
    void forbiddenForUser() {
        assertThat(mockMvc.post().uri(URI).cookie(accessCookie(UserRole.ROLE_USER))
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-021"));

        then(couponAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("토큰이 없으면 401이다")
    void unauthenticated() {
        assertThat(mockMvc.get().uri(URI))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        then(couponAdminService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("목록에 사용 수가 포함되어 직렬화된다")
    void listSerializes() {
        given(couponAdminService.getAll()).willReturn(List.of(new CouponAdminResponse(
                "coupon-pub-1", "가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO,
                100, LocalDateTime.of(2026, 12, 31, 23, 59, 59), true, 3)));

        assertThat(mockMvc.get().uri(URI).cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.data[0].code", c -> assertThat(c).isEqualTo("WELCOME-PRO"))
                .hasPathSatisfying("$.data[0].redeemedCount", r -> assertThat(r).isEqualTo(3))
                .hasPathSatisfying("$.data[0].active", a -> assertThat(a).isEqualTo(true));
    }

    @Test
    @DisplayName("비활성화는 경로의 publicId가 서비스로 간다")
    void deactivatePassesPublicId() {
        assertThat(mockMvc.patch().uri(URI + "/coupon-pub-1/deactivate")
                .cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatusOk();

        then(couponAdminService).should().deactivate("coupon-pub-1");
    }

    @Test
    @DisplayName("없는 쿠폰 비활성화는 HTTP 404 COUPON-001로 나간다")
    void deactivateUnknownIs404() {
        willThrow(new BusinessException(CouponErrorCode.COUPON_NOT_FOUND))
                .given(couponAdminService).deactivate("no-such-id");

        assertThat(mockMvc.patch().uri(URI + "/no-such-id/deactivate")
                .cookie(accessCookie(UserRole.ROLE_ADMIN)))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("COUPON-001"));
    }

    private Cookie accessCookie(UserRole role) {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(ADMIN_PUBLIC_ID, role, UserStatus.ACTIVE).value());
    }
}