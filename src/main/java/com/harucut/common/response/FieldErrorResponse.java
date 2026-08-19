package com.harucut.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "검증에 실패한 필드 하나")
public record FieldErrorResponse(

        @Schema(description = "필드 이름", example = "title")
        String field,

        @Schema(description = "실패 사유", example = "제목은 필수입니다.")
        String message
) {
}
