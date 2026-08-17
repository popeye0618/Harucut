package com.harucut.frame.converter;

import com.harucut.frame.attributes.BackgroundAttributes;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Converter
@RequiredArgsConstructor
public class BackgroundConverter implements AttributeConverter<BackgroundAttributes, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(BackgroundAttributes attribute) {
        if (attribute == null) {
            return null;
        }
        return objectMapper.writeValueAsString(attribute);
    }

    @Override
    public BackgroundAttributes convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, BackgroundAttributes.class);
        } catch (JacksonException e) {
            log.error("저장된 배경 JSON 역직렬화 실패: {}", excerpt(dbData), e);
            throw new IllegalStateException("저장된 배경 JSON이 깨져 있다", e);
        }
    }

    private String excerpt(String value) {
        return value.length() > 200 ? value.substring(0, 200) : value;
    }
}
