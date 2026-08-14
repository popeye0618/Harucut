package com.harucut.auth.jwt;

import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;

public record JwtClaims(
        String publicId,
        TokenType type,
        UserRole role,
        UserStatus status
) {
}
