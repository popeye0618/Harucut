package com.harucut.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 요청 (로그인 전)")
public record ResetPasswordRequest(

        @NotBlank
        @Schema(description = "코드 검증에서 받은 리셋 토큰. 10분 유효, **한 번 쓰면 사라진다**",
                example = "550e8400-e29b-41d4-a716-446655440000", requiredMode = Schema.RequiredMode.REQUIRED)
        String resetToken,

        @NotBlank @Size(min = 8, max = 20)
        @Schema(description = "새 비밀번호. 8~20자", example = "newpassword123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword
) {
    public ResetPasswordRequest {
        resetToken = resetToken == null ? null : resetToken.trim();
    }
}
