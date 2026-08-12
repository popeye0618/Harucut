package com.harucut.auth.cookie;

import com.harucut.auth.jwt.IssuedToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class CookieManagerTest {

    private static final IssuedToken TOKEN = new IssuedToken("token-value", Duration.ofMinutes(30));

    @Test
    @DisplayName("토큰 쿠키는 HttpOnly이고 만료가 토큰 ttl과 같다")
    void createsHttpOnlyCookie() {
        ResponseCookie cookie = new CookieManager(new CookieProperties("harucut.com", true, "Lax"))
                .createTokenCookie(CookieManager.ACCESS_TOKEN, TOKEN);

        assertThat(cookie.getName()).isEqualTo("accessToken");
        assertThat(cookie.getValue()).isEqualTo("token-value");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(30));
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getDomain()).isEqualTo("harucut.com");
    }

    @Test
    @DisplayName("secure=false 설정이면 Secure가 빠진다")
    void respectsSecureProperty() {
        ResponseCookie cookie = new CookieManager(new CookieProperties("localhost", false, "Lax"))
                .createTokenCookie(CookieManager.ACCESS_TOKEN, TOKEN);

        assertThat(cookie.isSecure()).isFalse();
    }
}