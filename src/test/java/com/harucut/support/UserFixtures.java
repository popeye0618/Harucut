package com.harucut.support;

import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import org.springframework.test.util.ReflectionTestUtils;

public final class UserFixtures {
    private UserFixtures() {
    }

    public static User localUser(String email, String encodedPassword) {
        return User.localUser(email, encodedPassword, "하루컷");
    }

    public static User localUser(String email, String encodedPassword, UserStatus status, UserRole role) {
        User user = localUser(email, encodedPassword);
        ReflectionTestUtils.setField(user, "userStatus", status);
        ReflectionTestUtils.setField(user, "userRole", role);
        return user;
    }

    public static User socialUser(String email, Provider provider) {
        User user = User.localUser(email, null, "소셜유저");
        ReflectionTestUtils.setField(user, "provider", provider);
        return user;
    }

    public static User socialUser(String email, Provider provider, String providerId) {
        User user = socialUser(email, provider);
        ReflectionTestUtils.setField(user, "providerId", providerId);
        return user;
    }
}
