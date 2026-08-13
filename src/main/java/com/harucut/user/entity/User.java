package com.harucut.user.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import com.harucut.user.enums.Provider;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_users_provider_email", columnNames = {"provider", "email"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    private static final String DEFAULT_PROFILE_IMAGE = "resources/defaults/userDefaultImage.png";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 12)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Provider provider;

    @Column(name = "provider_id", length = 64)
    private String providerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    @Column(nullable = false)
    private String email;

    @Column(length = 100)
    private String password;

    @Column(nullable = false, length = 20)
    private String username;

    @Column(name = "profile_image_url", nullable = false, length = 1024)
    private String profileImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", nullable = false, length = 20)
    private UserStatus userStatus;

    @Column(name = "delete_requested_at")
    private LocalDateTime deleteRequestedAt;

    private User(Provider provider, String providerId, String email,
                 String password, String username) {
        this.publicId = PublicIds.generate();
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.password = password;
        this.username = username;
        this.userRole = UserRole.ROLE_USER;
        this.userStatus = UserStatus.ACTIVE;
        this.profileImageUrl = DEFAULT_PROFILE_IMAGE;
    }

    public static User localUser(String email, String encodedPassword, String username) {
        return new User(Provider.HARUCUT, null, email, encodedPassword, username);
    }
}
