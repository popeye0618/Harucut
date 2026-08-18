package com.harucut.config.openapi;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * <b>문서 전용</b> 에러 응답 스키마. 런타임 직렬화에는 전혀 쓰이지 않는다.
 *
 * <p>왜 따로 두는가: springdoc은 {@code @ApiResponse} 에 {@code content} 를 안 주면
 * 그 상태 코드의 스키마를 <b>컨트롤러 메서드의 반환 타입</b>으로 채운다. 이 프로젝트는 성공·실패를
 * 모두 {@code Response<T>} 로 감싸므로, 그대로 두면 401 응답 문서에 성공 타입({@code ResponseFrameResponse})이
 * 붙는다. {@link ApiErrorDocumentationCustomizer} 가 4xx/5xx 스키마를 전부 이 타입으로 바꾼다.
 *
 * <p>실제 응답 형태의 원본은 {@code com.harucut.common.response.Response} 와
 * {@code com.harucut.common.exception.GlobalExceptionHandler} 다.
 */
@Schema(name = "ErrorResponse", description = "에러 응답 봉투")
public record ErrorResponse(

        @Schema(description = "에러 코드. **프론트는 HTTP 상태가 아니라 이 값으로 분기한다.**", example = "AUTH-011")
        String code,

        @Schema(description = "HTTP 상태 코드", example = "401")
        int status,

        @Schema(description = "에러 메시지", example = "Invalid access token.")
        String message
) {
}
