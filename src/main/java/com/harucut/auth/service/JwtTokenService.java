package com.harucut.auth.service;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.jwt.JwtClaims;
import com.harucut.auth.jwt.JwtProperties;
import com.harucut.auth.jwt.TokenType;
import com.harucut.common.exception.BusinessException;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtTokenService {

    private static final String ISSUER = "Harucut";
    private static final String TYPE_CLAIM = "type";
    private static final String ROLE_CLAIM = "role";
    private static final String STATUS_CLAIM = "status";

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

    public IssuedToken createAccessToken(String publicId, UserRole role, UserStatus status) {
        String value = base(publicId, TokenType.ACCESS, accessExpiration)
                .claim(ROLE_CLAIM, role.name())
                .claim(STATUS_CLAIM, status.name())
                .signWith(key)
                .compact();

        return new IssuedToken(value, accessExpiration);
    }

    public IssuedToken createRefreshToken(String publicId) {
        String value = base(publicId, TokenType.REFRESH, refreshExpiration)
                .signWith(key)
                .compact();

        return new IssuedToken(value, refreshExpiration);
    }

    public JwtClaims parse(String token) {
        try {
            Claims payload = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            TokenType type = toTokenType(payload.get(TYPE_CLAIM, String.class));
            boolean access = type == TokenType.ACCESS;

            return new JwtClaims(
                    payload.getSubject(),
                    type,
                    access ? required(payload, ROLE_CLAIM, UserRole::valueOf) : null,
                    access ? required(payload, STATUS_CLAIM, UserStatus::valueOf) : null
            );
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private <T> T required(Claims payload, String name, Function<String, T> mapper) {
        String raw = payload.get(name, String.class);
        if (raw == null) {
            throw new IllegalArgumentException("missing claim: " + name);
        }
        return mapper.apply(raw);
    }

    private JwtBuilder base(String publicId, TokenType type, Duration ttl) {
        Instant now = clock.instant();

        return Jwts.builder()
                .subject(publicId)
                .issuer(ISSUER)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .claim(TYPE_CLAIM, type.name());
    }

    private TokenType toTokenType(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("missing type claim");
        }
        return TokenType.valueOf(raw);
    }
}
