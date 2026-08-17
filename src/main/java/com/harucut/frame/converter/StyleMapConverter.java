package com.harucut.frame.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@Converter
@RequiredArgsConstructor
public class StyleMapConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final String EMPTY_JSON = "{}";

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return EMPTY_JSON;
        }
        return objectMapper.writeValueAsString(attribute);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {
            });
        } catch (JacksonException e) {
            log.warn("스타일 JSON 역직렬화 실패, 빈 맵으로 대체: {}", dbData, e);
            return Map.of();
        }
    }
}
