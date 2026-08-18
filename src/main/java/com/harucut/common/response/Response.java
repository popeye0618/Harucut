package com.harucut.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.harucut.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "모든 응답이 담기는 공통 봉투")
public record Response<T>(

        @Schema(description = "성공은 항상 GEN-000. 실패는 도메인별 에러 코드", example = "GEN-000")
        String code,

        @Schema(description = "HTTP 상태 코드", example = "200")
        int status,

        @Schema(description = "**실패일 때만 실린다.** 성공 응답에는 키 자체가 없다")
        String message,

        @Schema(description = "응답 본문. 없으면 키 자체가 없다")
        T data
) {

    private static final String OK_CODE = "GEN-000";
    private static final int OK_STATUS = 200;

    public static Response<Void> ok() {
        return new Response<>(OK_CODE, OK_STATUS, null, null);
    }

    public static <T> Response<T> ok(T data) {
        return new Response<>(OK_CODE, OK_STATUS, null, data);
    }

    public static <T> Response<T> error(ErrorCode errorCode) {
        return new Response<>(errorCode.getCode(), errorCode.getHttpStatus().value(), errorCode.getMessage(), null);
    }

    public static <T> Response<T> error(ErrorCode errorCode, T data) {
        return new Response<>(errorCode.getCode(), errorCode.getHttpStatus().value(), errorCode.getMessage(), data);
    }
}
