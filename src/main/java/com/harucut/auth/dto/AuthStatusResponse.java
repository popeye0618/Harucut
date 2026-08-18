package com.harucut.auth.dto;

import com.harucut.user.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "현재 로그인 상태")
public record AuthStatusResponse(

        @Schema(description = "토큰에 담긴 계정 상태. DB 를 조회하지 않는다", example = "ACTIVE")
        UserStatus userStatus
) {
}
