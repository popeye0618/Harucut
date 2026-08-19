package com.harucut.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "약관 생성 요청")
public record CreateTermsRequest(

        @NotBlank(message = "약관 코드는 필수입니다.")
        @Pattern(regexp = "^[a-z0-9-]{1,50}$", message = "약관 코드는 소문자·숫자·하이픈 1~50자여야 합니다.")
        @Schema(description = """
                프론트가 상수로 쓸 slug. 소문자·숫자·하이픈 1~50자.
                ⚠️ **한 번 정하면 바꿀 수 없다** — 수정 API 가 없다.""",
                example = "tos", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        @Schema(description = "제목. 최대 100자. 이것도 나중에 못 고친다", example = "이용약관",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @NotNull(message = "필수 동의 여부는 필수입니다.")
        @Schema(description = "필수 동의 여부. 나중에 못 고친다", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean required,

        @NotBlank(message = "본문은 필수입니다.")
        @Schema(description = "버전 1이 될 본문. 본문만 개정 API 로 바꿀 수 있다", example = "제1조 (목적) ...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String content
) {
}
