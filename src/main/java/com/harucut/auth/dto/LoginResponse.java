package com.harucut.auth.dto;

import com.harucut.user.enums.UserStatus;

public record LoginResponse(
        UserStatus userStatus
) {
}
