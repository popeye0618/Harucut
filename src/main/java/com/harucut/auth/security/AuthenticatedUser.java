package com.harucut.auth.security;

import com.harucut.auth.jwt.JwtClaims;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import org.springframework.security.core.AuthenticatedPrincipal;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

public record AuthenticatedUser(
        String publicId,
        UserRole role,
        UserStatus status
) implements AuthenticatedPrincipal {

    private static final String DELETED_REQUESTED_ROLE = "ROLE_DELETED_REQUESTED";

    public static AuthenticatedUser from(JwtClaims claims) {
        return new AuthenticatedUser(claims.publicId(), claims.role(), claims.status());
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return switch(status) {
            case ACTIVE -> List.of(new SimpleGrantedAuthority(role.name()));
            case DELETED_REQUESTED -> List.of(new SimpleGrantedAuthority(DELETED_REQUESTED_ROLE));
            case BLOCKED, DELETED -> List.of();
        };
    }

    @Override
    public String getName() {
        return publicId;
    }
}
