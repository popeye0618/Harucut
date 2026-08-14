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
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final JwtTokenService jwtTokenService;
    private final CookieManager cookieManager;

    public void save(String publicId, IssuedToken refreshToken) {
        repository.save(publicId, refreshToken);
    }

    public AuthTokenCookies reissue(String refreshToken) {
        JwtClaims claims = jwtTokenService.parse(refreshToken);

        if (claims.type() != TokenType.REFRESH) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        IssuedToken candidate = jwtTokenService.createRefreshToken(claims.publicId());
        RotationResult result = repository.rotate(claims.publicId(), refreshToken, candidate);

        String rotated = switch (result) {
            case RotationResult.Rotated r -> r.refreshToken();
            case RotationResult.Graced g -> g.refreshToken();
            case RotationResult.NoSession ignored ->
                    throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            case RotationResult.ReuseDetected ignored -> {
                log.warn("[reissue] refresh 재사용 감지. 세션 끊음. publicId={}", claims.publicId());
                throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
            }
        };

        IssuedToken newAccess = jwtTokenService.createAccessToken(claims.publicId());

        return new AuthTokenCookies(
                cookieManager.createTokenCookie(CookieManager.ACCESS_TOKEN, newAccess),
                cookieManager.createTokenCookie(CookieManager.REFRESH_TOKEN,
                        new IssuedToken(rotated, candidate.ttl()))
        );
    }

    public void revoke(String publicId) {
        repository.delete(publicId);
    }
}
