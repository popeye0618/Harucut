package com.harucut.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "약관 개정 요청")
public record ReviseTermsRequest(

        @NotBlank(message = "본문은 필수입니다.")
        @Schema(description = "새 버전의 본문. 이전 버전은 지워지지 않고 그대로 남는다",
                example = "제1조 (목적, 개정) ...", requiredMode = Schema.RequiredMode.REQUIRED)
        String content
) {
}
