package com.harucut.media.converter;

import com.harucut.media.compose.ComposeSpec;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

// 렌더 스펙 ↔ JSON 컬럼. 스펙은 장식이 아니라 합성의 전부라 깨져 있으면 크게 실패한다 —
// BackgroundConverter와 같은 철학. 깨진 스펙으로 그리는 척하면 결과물이 조용히 엉킨다
@Slf4j
@Converter
@RequiredArgsConstructor
public class ComposeSpecConverter implements AttributeConverter<ComposeSpec, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(ComposeSpec attribute) {
        if (attribute == null) {
            return null;
        }
        return objectMapper.writeValueAsString(attribute);
    }

    @Override
    public ComposeSpec convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, ComposeSpec.class);
        } catch (JacksonException e) {
            log.error("저장된 렌더 스펙 JSON 역직렬화 실패: {}", excerpt(dbData), e);
            throw new IllegalStateException("저장된 렌더 스펙 JSON이 깨져 있다", e);
        }
    }

    private String excerpt(String value) {
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
