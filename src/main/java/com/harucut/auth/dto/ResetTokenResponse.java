package com.harucut.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "리셋 토큰")
public record ResetTokenResponse(

        @Schema(description = "비밀번호 재설정에 쓸 1회용 토큰. **10분 안에 써야 한다**",
                example = "550e8400-e29b-41d4-a716-446655440000")
        String resetToken
) {
}
