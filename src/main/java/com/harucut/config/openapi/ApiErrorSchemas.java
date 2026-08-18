package com.harucut.config.openapi;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;

/**
 * 문서 전용 에러 스키마를 완성된 문서의 {@code components.schemas} 에 넣는다.
 *
 * <p><b>등록 시점이 중요하다.</b> 처음에는 {@code OpenAPI} 빈을 만들 때 등록했는데
 * ({@code GroupedOpenApi} 를 쓰지 않던 코틀린 구현이 그렇게 한다) 문서에서 사라졌다.
 * 그룹을 쓰면 springdoc 이 그룹마다 컨트롤러를 훑어 {@code components.schemas} 를 <b>새로 계산</b>하기
 * 때문이다. 빈에 미리 넣어둔 스키마는 그 계산 결과에 포함되지 않아, operation 의
 * {@code $ref: '#/components/schemas/ErrorResponse'} 만 남고 정의는 없는 끊어진 참조가 된다.
 * Swagger UI 는 이때 에러를 띄우지 않고 응답 예시만 조용히 비운다.
 *
 * <p>그래서 문서 전체가 다 만들어진 뒤 실행되는 {@link ApiErrorDocumentationCustomizer} 에서 넣는다.
 */
final class ApiErrorSchemas {

    static final String ERROR_REF = "#/components/schemas/ErrorResponse";
    static final String VALIDATION_REF = "#/components/schemas/ValidationErrorResponse";

    private ApiErrorSchemas() {
    }

    static void registerOn(OpenAPI openAPI) {
        if (openAPI.getComponents() == null) {
            openAPI.setComponents(new Components());
        }
        register(openAPI, ErrorResponse.class);
        register(openAPI, ValidationErrorResponse.class);
    }

    private static void register(OpenAPI openAPI, Class<?> type) {
        ModelConverters.getInstance()
                .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true))
                .referencedSchemas
                .forEach((name, schema) -> openAPI.getComponents().addSchemas(name, schema));
    }
}
