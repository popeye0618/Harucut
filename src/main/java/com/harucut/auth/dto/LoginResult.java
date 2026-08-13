package com.harucut.auth.dto;

import com.harucut.user.enums.UserStatus;
import org.springframework.http.ResponseCookie;

public record LoginResult(
        ResponseCookie accessTokenCookie,
        ResponseCookie refreshTokenCookie,
        UserStatus userStatus
) {
}
