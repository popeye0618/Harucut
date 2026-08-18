package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "가입용 인증 코드 발송 요청")
public record SendCodeRequest(

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "이메일. 대소문자·공백은 서버에서 정규화한다", example = "user@harucut.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email
) {
    public SendCodeRequest {
        email = Emails.normalize(email);
    }
}
