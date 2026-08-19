package com.harucut.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "닉네임 변경 요청")
public record ChangeUsernameRequest(

        @NotBlank @Size(max = 20)
        @Schema(description = """
                새 닉네임. 최대 20자. **앞뒤 공백은 서버가 잘라낸다** —
                공백만 보내면 잘린 뒤 빈 값이 되어 `GEN-003` 이다. 중복은 허용한다.""",
                example = "하루컷", requiredMode = Schema.RequiredMode.REQUIRED)
        String username
) {
    public ChangeUsernameRequest {
        username = (username == null) ? null : username.strip();
    }
}
