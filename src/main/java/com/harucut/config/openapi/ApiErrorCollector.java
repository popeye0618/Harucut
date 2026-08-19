package com.harucut.config.openapi;

import com.harucut.common.exception.GlobalErrorCode;
import io.swagger.v3.oas.models.Operation;
import jakarta.validation.Valid;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 핸들러 메서드에 붙은 {@link ApiErrors} 를 읽어 operation 에 임시로 실어둔다.
 *
 * <p>실제 문서 렌더링은 {@link ApiErrorDocumentationCustomizer} 가 한다. 두 단계로 나눈 이유는
 * <b>여기서는 URL 을 모르기 때문</b>이다. springdoc 의 {@code OperationCustomizer} 는 애노테이션 정보를
 * 담은 {@code HandlerMethod} 는 주지만 경로 문자열은 주지 않는다. 반대로 문서 전체를 받는
 * {@code OpenApiCustomizer} 는 경로는 알지만 핸들러를 모른다. 그래서 여기서 애노테이션을 읽어
 * 확장 필드에 담아 넘기고, 렌더링은 경로를 아는 쪽에서 한 번에 한다.
 */
@Component
public class ApiErrorCollector implements GlobalOperationCustomizer {

    static final String EXTENSION_KEY = "x-harucut-errors";
    static final String ROLE_REQUIRED_KEY = "x-harucut-role-required";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Set<String> declared = new LinkedHashSet<>();

        ApiErrors onType = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), ApiErrors.class);
        if (onType != null) {
            declared.addAll(Arrays.asList(onType.value()));
        }

        ApiErrors onMethod = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), ApiErrors.class);
        if (onMethod != null) {
            declared.addAll(Arrays.asList(onMethod.value()));
        }

        // @Valid @RequestBody 가 있으면 GEN-003 은 반드시 날 수 있다. 62곳에 손으로 적게 하지 않는다.
        if (hasValidatedRequestBody(handlerMethod)) {
            declared.add(GlobalErrorCode.VALIDATION_FAILED.getCode() + ": 요청 본문 검증 실패");
        }

        if (!declared.isEmpty()) {
            operation.addExtension(EXTENSION_KEY, new ArrayList<>(declared));
        }
        if (requiresRole(handlerMethod)) {
            operation.addExtension(ROLE_REQUIRED_KEY, Boolean.TRUE);
        }
        return operation;
    }

    /**
     * 메서드나 클래스에 {@code @PreAuthorize} 가 붙어 있으면 역할 검사가 있다는 뜻이고, 403 이 날 수 있다.
     * 경로 규칙({@code SecurityPaths})만으로는 알 수 없는 정보라 여기서 읽어 넘긴다.
     */
    private boolean requiresRole(HandlerMethod handlerMethod) {
        return AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PreAuthorize.class);
    }

    static boolean roleRequired(Operation operation) {
        return operation.getExtensions() != null
                && Boolean.TRUE.equals(operation.getExtensions().get(ROLE_REQUIRED_KEY));
    }

    /**
     * {@code @Valid} 가 {@code @RequestBody} 에 붙었을 때만 GEN-003 이다.
     * 모델 애트리뷰트나 파라미터 검증 실패는 {@code BindException} 경로라 GEN-002 로 나간다.
     */
    private boolean hasValidatedRequestBody(HandlerMethod handlerMethod) {
        return Arrays.stream(handlerMethod.getMethodParameters())
                .anyMatch(this::isValidatedRequestBody);
    }

    private boolean isValidatedRequestBody(MethodParameter parameter) {
        boolean body = parameter.hasParameterAnnotation(RequestBody.class);
        boolean validated = parameter.hasParameterAnnotation(Valid.class)
                || parameter.hasParameterAnnotation(Validated.class);

        return body && validated;
    }

    @SuppressWarnings("unchecked")
    static List<String> declaredOn(Operation operation) {
        if (operation.getExtensions() == null) {
            return List.of();
        }
        Object raw = operation.getExtensions().get(EXTENSION_KEY);
        return raw instanceof List<?> list ? (List<String>) list : List.of();
    }
}
