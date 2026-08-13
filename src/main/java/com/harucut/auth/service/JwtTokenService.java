package com.harucut.auth.service;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtTokenService {

    private static final String ISSUER = "Harucut";
    private static final String TYPE_CLAIM = "type";

    private final SecretKey key;
    private final Duration accessExpiration;
    private final Duration refreshExpiration;
    private final Clock clock;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = properties.accessExpiration();
        this.refreshExpiration = properties.refreshExpiration();
        this.clock = clock;
    }

    public IssuedToken createAccessToken(String publicId) {
        return create(publicId, TokenType.ACCESS, accessExpiration);
    }

    public IssuedToken createRefreshToken(String publicId) {
        return create(publicId, TokenType.REFRESH, refreshExpiration);
    }

    public JwtClaims parse(String token) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new JwtClaims(
                    payload.getSubject(),
                    toTokenType(payload.get(TYPE_CLAIM, String.class))
            );
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private IssuedToken create(String publicId, TokenType type, Duration ttl) {
        Instant now = clock.instant();

        String value = Jwts.builder()
                .subject(publicId)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(TYPE_CLAIM, type.name())
                .signWith(key)
                .compact();

        return new IssuedToken(value, ttl);
    }

    private TokenType toTokenType(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing type claim");
        }
        return TokenType.valueOf(raw);
    }
}
