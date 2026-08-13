package com.harucut.auth.security;

import com.harucut.user.entity.User;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserPrincipal implements UserDetails {

    private static final String DELETED_REQUESTED_ROLE = "ROLE_DELETED_REQUESTED";

    private final Long id;
    private final String publicId;
    private final String email;
    private final String password;
    private final UserRole userRole;
    private final UserStatus userStatus;

    public CustomUserPrincipal(User user) {
        this.id = user.getId();
        this.publicId = user.getPublicId();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.userRole = user.getUserRole();
        this.userStatus = user.getUserStatus();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return switch(userStatus) {
            case ACTIVE -> List.of(new SimpleGrantedAuthority(userRole.name()));
            case DELETED_REQUESTED -> List.of(new SimpleGrantedAuthority(DELETED_REQUESTED_ROLE));
            case BLOCKED, DELETED -> List.of();
        };
    }

    @Override
    public String getUsername() {
        return email;
    }
}
