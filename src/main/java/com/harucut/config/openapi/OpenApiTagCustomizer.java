package com.harucut.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 그룹 문서에서 그 그룹이 쓰지 않는 태그를 걷어낸다.
 *
 * <p>{@code OpenApiConfig.tags()} 는 전체 태그를 순서대로 선언한다. 그런데 springdoc 은 그 목록을
 * <b>모든 그룹 문서에 그대로 복사</b>한다. 그래서 관리자 그룹 문서가 "네컷 합성", "파일 업로드" 같은
 * 쓰지도 않는 태그를 23개 다 달고 다닌다. 문서를 읽는 사람에게는 빈 섹션이고, 코드 생성기에게는 거짓말이다.
 *
 * <p>선언 순서는 그대로 두고 걸러내기만 한다. 순서가 곧 화면 순서이기 때문이다.
 */
@Component
public class OpenApiTagCustomizer implements GlobalOpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getTags() == null || openApi.getPaths() == null) {
            return;
        }

        Set<String> used = openApi.getPaths().values().stream()
                .map(PathItem::readOperations)
                .flatMap(List::stream)
                .map(Operation::getTags)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        List<Tag> declaredAndUsed = openApi.getTags().stream()
                .filter(tag -> used.contains(tag.getName()))
                .collect(Collectors.toCollection(ArrayList::new));

        openApi.setTags(declaredAndUsed);
    }
}
