package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "이메일 회원가입 요청")
public record RegisterRequest(

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "**이 이메일로 인증을 먼저 마쳐야 한다.** 인증 후 10분 안에 가입해야 한다",
                example = "user@harucut.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank @Size(max = 20)
        @Schema(description = "닉네임. 최대 20자", example = "하루컷", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        @NotBlank @Size(min = 8, max = 20)
        @Schema(description = "비밀번호. 8~20자", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
    public RegisterRequest {
        email = Emails.normalize(email);
    }
}
