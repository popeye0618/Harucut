package com.harucut.auth.dto;

import org.springframework.http.ResponseCookie;

public record AuthTokenCookies(
        ResponseCookie accessTokenCookie,
        ResponseCookie refreshTokenCookie
) {
}
