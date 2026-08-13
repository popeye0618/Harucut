package com.harucut.auth.cookie;

import com.harucut.auth.jwt.IssuedToken;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CookieManager {

    public static final String ACCESS_TOKEN = "accessToken";
    public static final String REFRESH_TOKEN = "refreshToken";

    private final CookieProperties properties;

    public ResponseCookie createExpiredCookie(String name) {
        return base(name, "")
                .maxAge(0)
                .build();
    }

    public ResponseCookie createTokenCookie(String name, IssuedToken token) {
        return base(name, token.value())
                .maxAge(token.ttl())
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.secure())
                .path("/")
                .sameSite(properties.sameSite())
                .domain(properties.domain());
    }
}
