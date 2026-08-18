package com.harucut.config.openapi;

import com.harucut.common.exception.ErrorCode;
import com.harucut.common.exception.GlobalErrorCode;
import io.swagger.v3.oas.models.examples.Example;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 문서에 실을 에러 하나. {@link ApiErrors} 의 {@code "코드: 설명"} 문자열을 실제 {@code ErrorCode} 와 합친 것.
 */
record DocumentedError(String code, int status, String message, String note) {

    static DocumentedError parse(String raw) {
        int separator = raw.indexOf(':');
        String code = (separator < 0 ? raw : raw.substring(0, separator)).trim();
        String note = separator < 0 ? "" : raw.substring(separator + 1).trim();

        return of(ErrorCodeCatalog.require(code), note);
    }

    static DocumentedError of(ErrorCode errorCode, String note) {
        return new DocumentedError(
                errorCode.getCode(), errorCode.getHttpStatus().value(), errorCode.getMessage(), note);
    }

    /**
     * 같은 에러 코드가 서로 다른 사유로 날 수 있다. 예를 들어 프로필 이미지 변경의 403 은
     * "탈퇴 요청 상태"(전역 인가 규칙)일 수도, "남의 s3Key"(도메인 규칙)일 수도 있다.
     * 뒤에 선언된 것이 앞의 것을 덮어쓰면 사유 하나가 문서에서 사라지므로 설명을 잇는다.
     */
    DocumentedError mergedWith(DocumentedError other) {
        if (other.note.isBlank() || note.equals(other.note)) {
            return this;
        }
        if (note.isBlank()) {
            return other;
        }
        return new DocumentedError(code, status, message, note + " / " + other.note);
    }

    /** Swagger UI 의 응답 설명에 들어갈 한 줄. 마크다운으로 렌더된다. */
    String descriptionLine() {
        return note.isBlank()
                ? "- `%s` — %s".formatted(code, message)
                : "- `%s` — %s _(%s)_".formatted(code, note, message);
    }

    /** 실제로 내려가는 JSON 과 같은 모양의 예시. 프론트가 그대로 복사해 쓸 수 있어야 한다. */
    Example toExample() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("status", status);
        body.put("message", message);

        // GEN-003 만 data 에 필드별 실패 목록이 실린다 (GlobalExceptionHandler 참고).
        if (GlobalErrorCode.VALIDATION_FAILED.getCode().equals(code)) {
            body.put("data", List.of(Map.of("field", "title", "message", "제목은 필수입니다.")));
        }

        Example example = new Example();
        example.setSummary(note.isBlank() ? code : code + " — " + note);
        example.setValue(body);
        return example;
    }
}
