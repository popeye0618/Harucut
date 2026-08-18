package com.harucut.coupon.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.security.CustomAccessDeniedHandler;
import com.harucut.auth.security.CustomAuthenticationEntryPoint;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.common.exception.BusinessException;
import com.harucut.config.SecurityConfig;
import com.harucut.coupon.dto.MyCouponResponse;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.service.CouponService;
import com.harucut.subscription.enums.PlanTier;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@WebMvcTest(CouponController.class)
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class,
        JwtTokenService.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("CouponController")
class CouponControllerTest extends SecurityBeansMockSupport {

    private static final String REDEEM_URI = "/api/auth/coupons/redeem";
    private static final String LIST_URI = "/api/auth/coupons";
    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);
    private static final String VALID_BODY = """
            {
              "code": "WELCOME-PRO"
            }
            """;

    @Autowired
    private MockMvcTester mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @MockitoBean
    private CouponService couponService;

    @Test
    @DisplayName("사용 성공은 200이고 개시 정보가 직렬화된다")
    void redeemSerializesResponse() {
        given(couponService.redeem(PUBLIC_ID, "WELCOME-PRO"))
                .willReturn(new RedeemResponse(true, PlanTier.PRO, NOW, NOW.plusMonths(1)));

        assertThat(post(VALID_BODY))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.data.applied", a -> assertThat(a).isEqualTo(true))
                .hasPathSatisfying("$.data.grantTier", g -> assertThat(g).isEqualTo("PRO"))
                .hasPathSatisfying("$.data.startsAt", s -> assertThat(s).isEqualTo("2026-08-18T10:00:00"));
    }

    @Test
    @DisplayName("principal의 publicId와 본문의 code가 서비스로 간다")
    void passesPrincipalAndCode() {
        given(couponService.redeem(PUBLIC_ID, "WELCOME-PRO"))
                .willReturn(new RedeemResponse(true, PlanTier.PRO, NOW, NOW.plusMonths(1)));

        post(VALID_BODY);

        then(couponService).should().redeem(PUBLIC_ID, "WELCOME-PRO");
    }

    @Test
    @DisplayName("code가 빈 값이면 400 GEN-003이고 서비스가 호출되지 않는다")
    void blankCode() {
        assertThat(post("""
                {
                  "code": ""
                }
                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("GEN-003"));

        then(couponService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("상한 도달은 HTTP 409 COUPON-005로 나간다")
    void exhaustedIs409() {
        given(couponService.redeem(PUBLIC_ID, "WELCOME-PRO"))
                .willThrow(new BusinessException(CouponErrorCode.COUPON_EXHAUSTED));

        assertThat(post(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .hasPathSatisfying("$.code", c -> assertThat(c).isEqualTo("COUPON-005"));
    }

    @Test
    @DisplayName("토큰이 없으면 401이고 서비스가 호출되지 않는다")
    void unauthenticated() {
        assertThat(mockMvc.post().uri(REDEEM_URI)
                .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        then(couponService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("내 쿠폰 목록이 배열로 직렬화된다")
    void listSerializes() {
        given(couponService.getMyCoupons(PUBLIC_ID)).willReturn(List.of(
                new MyCouponResponse("uc-pub-1", "가입 축하 PRO", PlanTier.PRO,
                        UserCouponStatus.RESERVED, NOW)));

        assertThat(mockMvc.get().uri(LIST_URI).cookie(accessCookie()))
                .hasStatusOk()
                .bodyJson()
                .hasPathSatisfying("$.data[0].publicId", p -> assertThat(p).isEqualTo("uc-pub-1"))
                .hasPathSatisfying("$.data[0].couponName", n -> assertThat(n).isEqualTo("가입 축하 PRO"))
                .hasPathSatisfying("$.data[0].status", s -> assertThat(s).isEqualTo("RESERVED"));
    }

    @Test
    @DisplayName("목록도 토큰이 없으면 401이다")
    void listUnauthenticated() {
        assertThat(mockMvc.get().uri(LIST_URI))
                .hasStatus(HttpStatus.UNAUTHORIZED);

        then(couponService).shouldHaveNoInteractions();
    }

    private MvcTestResult post(String body) {
        return mockMvc.post().uri(REDEEM_URI)
                .cookie(accessCookie())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private Cookie accessCookie() {
        return new Cookie(CookieManager.ACCESS_TOKEN, jwtTokenService
                .createAccessToken(PUBLIC_ID, UserRole.ROLE_USER, UserStatus.ACTIVE).value());
    }
}