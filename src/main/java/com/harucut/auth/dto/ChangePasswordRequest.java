package com.harucut.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 변경 요청 (로그인 상태)")
public record ChangePasswordRequest(

        @NotBlank
        @Schema(description = "현재 비밀번호", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
        String oldPassword,

        @NotBlank @Size(min = 8, max = 20)
        @Schema(description = "새 비밀번호. 8~20자", example = "newpassword123",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String newPassword
) {}
