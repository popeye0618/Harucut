package com.harucut.config.openapi;

import com.harucut.common.response.FieldErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * <b>문서 전용</b> — {@code @Valid @RequestBody} 검증 실패(GEN-003) 응답 스키마.
 *
 * <p>{@link ErrorResponse} 에 {@code data} 가 하나 더 붙은 형태다. 400 응답에 GEN-003 과
 * 다른 에러 코드가 함께 오는 경우가 있어서(예: 결제의 PAY-007), 400 스키마는 둘을 모두 담을 수 있는
 * 이 타입을 쓴다. {@code data} 는 GEN-003 일 때만 실리고 나머지는 {@code NON_NULL} 로 빠진다.
 */
@Schema(name = "ValidationErrorResponse", description = "검증 실패 응답 봉투 (GEN-003 은 data 포함)")
public record ValidationErrorResponse(

        @Schema(description = "에러 코드", example = "GEN-003")
        String code,

        @Schema(description = "HTTP 상태 코드", example = "400")
        int status,

        @Schema(description = "에러 메시지", example = "Validation failed.")
        String message,

        @Schema(description = "필드별 검증 실패 목록. GEN-003 일 때만 실린다")
        List<FieldErrorResponse> data
) {
}
