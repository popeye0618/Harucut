package com.harucut.auth.jwt;

public record JwtClaims(
        String publicId,
        TokenType type
) {
}
