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

    public ResponseCookie createTokenCookie(String name, IssuedToken token) {
        return ResponseCookie.from(name, token.value())
                .httpOnly(true)
                .secure(properties.secure())
                .path("/")
                .maxAge(token.ttl())
                .sameSite(properties.sameSite())
                .domain(properties.domain())
                .build();
    }
}
