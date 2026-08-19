package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 코드 발송 요청")
public record SendResetCodeRequest(

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "가입할 때 쓴 이메일. **소셜 계정은 대상이 아니다**", example = "user@harucut.com",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String email
) {
    public SendResetCodeRequest {
        email = Emails.normalize(email);
    }
}
