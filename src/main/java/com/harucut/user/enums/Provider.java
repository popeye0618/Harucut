package com.harucut.user.enums;

import java.util.Locale;

public enum Provider {
    GOOGLE, KAKAO, NAVER, APPLE, HARUCUT;

    public static Provider from(String registrationId) {
        return valueOf(registrationId.toUpperCase(Locale.ROOT));
    }
}
