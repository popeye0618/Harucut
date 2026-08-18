package com.harucut.config.openapi;

import io.swagger.v3.oas.models.examples.Example;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DocumentedError")
class DocumentedErrorTest {

    @Nested
    @DisplayName("parse — \"코드: 설명\" 문자열 해석")
    class Parse {

        @Test
        @DisplayName("상태 코드와 메시지는 적지 않아도 ErrorCode enum 에서 채워진다")
        void fillsStatusAndMessageFromEnum() {
            DocumentedError error = DocumentedError.parse("SUBS-002: 보관 기간이 지난 프레임");

            assertThat(error.code()).isEqualTo("SUBS-002");
            assertThat(error.status()).isEqualTo(403);
            assertThat(error.message()).isEqualTo("Requested history is beyond the plan's retention period.");
            assertThat(error.note()).isEqualTo("보관 기간이 지난 프레임");
        }

        @Test
        @DisplayName("설명은 생략할 수 있다")
        void noteIsOptional() {
            DocumentedError error = DocumentedError.parse("GEN-021");

            assertThat(error.note()).isEmpty();
            assertThat(error.status()).isEqualTo(403);
        }

        @Test
        @DisplayName("없는 코드는 예외로 막는다")
        void unknownCodeFails() {
            assertThatThrownBy(() -> DocumentedError.parse("NOPE-001: 없는 코드"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("toExample — 실제 응답과 같은 모양의 예시")
    class ToExample {

        @Test
        @DisplayName("일반 에러는 code/status/message 세 키만 담는다")
        void plainError() {
            Example example = DocumentedError.parse("AUTH-011").toExample();

            assertThat(example.getValue()).isInstanceOf(Map.class);
            assertThat(asMap(example))
                    .containsExactly(
                            Map.entry("code", "AUTH-011"),
                            Map.entry("status", 401),
                            Map.entry("message", "Invalid access token."));
        }

        @Test
        @DisplayName("GEN-003 만 data 에 필드별 실패 목록이 실린다")
        void validationErrorCarriesFieldErrors() {
            Example example = DocumentedError.parse("GEN-003").toExample();

            assertThat(asMap(example)).containsKey("data");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Example example) {
        return (Map<String, Object>) example.getValue();
    }

    @Nested
    @DisplayName("descriptionLine")
    class DescriptionLine {

        @Test
        @DisplayName("설명이 있으면 코드 · 설명 · 원문 메시지를 함께 보여준다")
        void withNote() {
            assertThat(DocumentedError.parse("GEN-021: 남의 프레임").descriptionLine())
                    .isEqualTo("- `GEN-021` — 남의 프레임 _(Access denied.)_");
        }

        @Test
        @DisplayName("설명이 없으면 메시지만 보여준다")
        void withoutNote() {
            assertThat(DocumentedError.parse("GEN-021").descriptionLine())
                    .isEqualTo("- `GEN-021` — Access denied.");
        }
    }
}
