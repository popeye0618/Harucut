package com.harucut.config.openapi;

import io.swagger.v3.oas.models.Operation;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApiErrorCollector")
class ApiErrorCollectorTest {

    private ApiErrorCollector collector;

    @BeforeEach
    void setUp() {
        collector = new ApiErrorCollector();
    }

    @Nested
    @DisplayName("customize")
    class Customize {

        @Test
        @DisplayName("메서드에 선언한 에러 코드를 모은다")
        void collectsMethodLevelErrors() throws Exception {
            Operation operation = collect("read");

            assertThat(ApiErrorCollector.declaredOn(operation))
                    .contains("NOTICE-001: 없거나 게시되지 않은 공지");
        }

        @Test
        @DisplayName("클래스에 선언한 에러 코드가 모든 메서드에 함께 붙는다")
        void mergesTypeLevelErrors() throws Exception {
            Operation operation = collect("create");

            assertThat(ApiErrorCollector.declaredOn(operation)).contains("GEN-002: 잘못된 입력");
        }

        @Test
        @DisplayName("@Valid @RequestBody 가 있으면 GEN-003 을 자동으로 붙인다")
        void addsValidationErrorForValidatedBody() throws Exception {
            Operation operation = collect("create");

            assertThat(ApiErrorCollector.declaredOn(operation)).contains("GEN-003: 요청 본문 검증 실패");
        }

        @Test
        @DisplayName("검증 대상 본문이 없으면 GEN-003 을 붙이지 않는다 — GEN-002 로 나가는 경로다")
        void skipsValidationErrorWithoutValidatedBody() throws Exception {
            Operation operation = collect("read");

            assertThat(ApiErrorCollector.declaredOn(operation))
                    .noneMatch(declared -> declared.startsWith("GEN-003"));
        }

        @Test
        @DisplayName("선언이 하나도 없으면 확장 필드를 만들지 않는다")
        void noExtensionWhenNothingDeclared() throws Exception {
            Operation operation = new Operation();
            collector.customize(operation, handlerMethod(Bare.class, new Bare(), "ping"));

            assertThat(operation.getExtensions()).isNull();
        }
    }

    private Operation collect(String methodName) throws Exception {
        Operation operation = new Operation();
        collector.customize(operation, handlerMethod(Sample.class, new Sample(), methodName));
        return operation;
    }

    private HandlerMethod handlerMethod(Class<?> type, Object bean, String name) throws Exception {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow();

        return new HandlerMethod(bean, method);
    }

    @ApiErrors("GEN-002: 잘못된 입력")
    static class Sample {

        @ApiErrors("NOTICE-001: 없거나 게시되지 않은 공지")
        public String read(Long id) {
            return "";
        }

        public String create(@RequestBody @Valid String body) {
            return body;
        }
    }

    static class Bare {

        public String ping() {
            return "pong";
        }
    }
}
