package com.harucut.auth.oauth2.unlink;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "kakao.auth")
public record KakaoAuthProperties(
        String adminKey,
        @DefaultValue("https://kapi.kakao.com/v1/user/unlink") String unlinkUrl,
        @DefaultValue("user_id") String unlinkTargetIdType
) {
}
