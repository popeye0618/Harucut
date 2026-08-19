package com.harucut.auth.dto;

import com.harucut.common.utils.Emails;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 코드 검증 요청")
public record VerifyResetCodeRequest(

        @NotBlank @Email @Size(max = 255)
        @Schema(description = "이메일. 대소문자·공백은 서버에서 정규화한다", example = "user@harucut.com", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,

        @NotBlank
        @Schema(description = "메일로 받은 6자리 코드. **대소문자를 가리지 않는다**", example = "483920",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code
) {
    public VerifyResetCodeRequest {
        email = Emails.normalize(email);
        code = code == null ? null : code.trim();
    }
}
