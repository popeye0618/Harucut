package com.harucut.auth.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.dto.AuthTokenCookies;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.TokenType;
import com.harucut.auth.security.CustomUserPrincipal;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.auth.service.RefreshTokenService;
import com.harucut.common.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class TokenController {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final CookieManager cookieManager;

    @PostMapping("/reissue")
    public ResponseEntity<Response<Void>> reissue(
            @CookieValue(CookieManager.REFRESH_TOKEN) String refreshToken
    ) {
        AuthTokenCookies cookies = refreshTokenService.reissue(refreshToken);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookies.accessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.refreshTokenCookie().toString())
                .body(Response.ok());
    }

    @DeleteMapping("/logout")
    public ResponseEntity<Response<Void>> logout(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @CookieValue(value = CookieManager.REFRESH_TOKEN, required = false) String refreshToken
    ) {
        resolvePublicId(principal, refreshToken).ifPresent(this::revokeQuietly);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, expired(CookieManager.ACCESS_TOKEN))
                .header(HttpHeaders.SET_COOKIE, expired(CookieManager.REFRESH_TOKEN))
                .body(Response.ok());
    }

    private void revokeQuietly(String publicId) {
        try {
            refreshTokenService.revoke(publicId);
        } catch (RuntimeException e) {
            log.warn("[logout] refresh 토큰 삭제 실패. TTL로 정리. publicId={}", publicId, e);
        }
    }

    private Optional<String> resolvePublicId(CustomUserPrincipal principal, String refreshToken) {
        if (principal != null) {
            return Optional.of(principal.getPublicId());
        }
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }

        try {
            JwtClaims claims = jwtTokenService.parse(refreshToken);
            return claims.type() == TokenType.REFRESH
                    ? Optional.of(claims.publicId())
                    : Optional.empty();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String expired(String name) {
        return cookieManager.createExpiredCookie(name).toString();
    }
}
