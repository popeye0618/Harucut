package com.harucut.auth.controller;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.dto.AuthTokenCookies;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.TokenType;
import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.auth.service.RefreshTokenService;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@Slf4j
@Tag(name = "인증 · 토큰")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/harucut")
public class TokenController {

    private final RefreshTokenService refreshTokenService;
    private final JwtTokenService jwtTokenService;
    private final CookieManager cookieManager;

    @Operation(
            summary = "토큰 재발급",
            description = """
                    `refreshToken` 쿠키를 보내면 access·refresh 를 **둘 다 새로 발급**해 쿠키로 내려준다.

                    ⚠️ **재발급 요청은 프론트에서 직렬화해야 한다.** access 가 만료된 순간 여러 요청이
                    동시에 재발급을 쏘면, 회전된 이전 토큰으로 들어온 요청이 거절될 수 있다.
                    서버가 10초의 유예 창을 두지만 그걸 넘기면 로그아웃된다.
                    **재발급은 하나만 보내고 나머지는 그 결과를 기다리게 할 것.**

                    `AUTH-012`(만료)와 `AUTH-011`(무효)의 처리가 다르다 — 둘 다 재로그인이지만,
                    `AUTH-011` 은 토큰 재사용이 감지된 경우도 포함하므로 조용히 로그인 화면으로 보낸다.
                    """)
    @ApiErrors({
            "GEN-004: refreshToken 쿠키가 아예 없음",
            "AUTH-012: refresh 토큰이 만료됨(14일) — 재로그인",
            "AUTH-011: refresh 토큰이 아니거나, 서명 불일치, 저장값 불일치, 재사용 감지 — 재로그인"
    })
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

    @Operation(
            summary = "로그아웃",
            description = """
                    **항상 200 이다.** 토큰이 없어도, 만료됐어도 성공한다.

                    로그아웃이 실패하면 사용자는 로그아웃조차 할 수 없게 된다. 그래서 인증을 요구하지 않고,
                    서버 상태 정리(저장된 refresh 삭제)는 되면 하고 안 되면 만료로 정리되게 뒀다.
                    응답에는 두 쿠키를 만료시키는 `Set-Cookie` 가 실린다.
                    """)
    @DeleteMapping("/logout")
    public ResponseEntity<Response<Void>> logout(
            @AuthenticationPrincipal AuthenticatedUser principal,
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

    private Optional<String> resolvePublicId(AuthenticatedUser principal, String refreshToken) {
        if (principal != null) {
            return Optional.of(principal.publicId());
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
