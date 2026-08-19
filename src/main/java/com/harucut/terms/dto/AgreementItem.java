package com.harucut.terms.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "동의/철회 항목 하나")
public record AgreementItem(

        @NotBlank(message = "약관 코드는 필수입니다.")
        @Schema(description = "약관 코드. 활성 약관이어야 한다", example = "tos",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @NotNull(message = "동의 여부는 필수입니다.")
        @Schema(description = "`true` 동의 · `false` 철회. **필수 약관은 `false` 로 보낼 수 없다**",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean agreed
) {
}
