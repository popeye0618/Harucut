package com.harucut.frame.converter;

import com.harucut.frame.entity.Frame;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

// 셀 누끼 토글 4개 ↔ JSON 배열. 장식이라 깨져도 조용히 "전부 끔"으로 내려앉는다 —
// StyleMapConverter와 같은 철학(장식 때문에 프레임을 못 보여주면 안 된다).
// null 컬럼(이 필드가 없던 시절의 행)도 같은 경로로 전부 끔이 된다
@Slf4j
@Converter
@RequiredArgsConstructor
public class CellCutoutsConverter implements AttributeConverter<List<Boolean>, String> {

    private final ObjectMapper objectMapper;

    @Override
    public String convertToDatabaseColumn(List<Boolean> attribute) {
        return objectMapper.writeValueAsString(Frame.normalizeCellCutouts(attribute));
    }

    @Override
    public List<Boolean> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return Frame.normalizeCellCutouts(null);
        }
        try {
            return Frame.normalizeCellCutouts(
                    objectMapper.readValue(dbData, new TypeReference<List<Boolean>>() {
                    }));
        } catch (JacksonException e) {
            log.warn("셀 누끼 JSON 역직렬화 실패, 전부 끔으로 대체: {}", dbData, e);
            return Frame.normalizeCellCutouts(null);
        }
    }
}
