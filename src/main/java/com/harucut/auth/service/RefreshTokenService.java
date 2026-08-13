package com.harucut.auth.service;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.dto.AuthTokenCookies;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String KEY_PREFIX = "REFRESH_TOKEN:USER:";

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenService jwtTokenService;
    private final CookieManager cookieManager;

    public void save(String publicId, IssuedToken refreshToken) {
        redisTemplate.opsForValue()
                .set(KEY_PREFIX + publicId, refreshToken.value(), refreshToken.ttl());
    }

    public AuthTokenCookies reissue(String refreshToken) {
        JwtClaims claims = jwtTokenService.parse(refreshToken);

        if (claims.type() != TokenType.REFRESH) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        verifyStored(claims.publicId(), refreshToken);

        IssuedToken newAccess = jwtTokenService.createAccessToken(claims.publicId());
        IssuedToken newRefresh = jwtTokenService.createRefreshToken(claims.publicId());
        save(claims.publicId(), newRefresh);

        return new AuthTokenCookies(
                cookieManager.createTokenCookie(CookieManager.ACCESS_TOKEN, newAccess),
                cookieManager.createTokenCookie(CookieManager.REFRESH_TOKEN, newRefresh)
        );
    }

    public void logout(String publicId) {
        try {
            redisTemplate.delete(KEY_PREFIX + publicId);
        } catch (RuntimeException e) {
            log.warn("[logout] Redis 삭제 실패. TTL로 정리. publicId={}", publicId, e);
        }
    }

    private void verifyStored(String publicId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(KEY_PREFIX + publicId);

        if (stored == null) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        if(!stored.equals(refreshToken)) {
            log.warn("[reissue] refresh 재사용 감지. 세션 끊음. publicId={}", publicId);
            redisTemplate.delete(KEY_PREFIX + publicId);
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

}
