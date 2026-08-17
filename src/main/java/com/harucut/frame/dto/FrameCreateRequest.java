package com.harucut.frame.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

// 생성(POST)과 수정(PUT)이 같은 본문을 쓴다
public record FrameCreateRequest(
        @NotBlank String title,
        String description,
        @NotBlank String previewKey,
        @NotNull FrameType frameType,

        Integer canvasWidth,
        Integer canvasHeight,
        @NotNull BackgroundAttributes background,
        // 셀 누끼(칸별 비네트) 토글, 촬영 슬롯 순서. 선택 — 안 보내면 전부 끔
        @Size(min = 4, max = 4) List<Boolean> cellCutouts,
        @Valid List<ComponentRequest> components
) {

    // "description 없으면 빈 문자열로 저장" 규칙의 소유자
    public String descriptionOrEmpty() {
        return description == null ? "" : description;
    }

    public record ComponentRequest(
            // 클라이언트 식별자 — 저장하지 않는다
            String id,
            @NotNull ComponentType type,
            @NotBlank String source,
            // TEXT 전용: 구워 올린 텍스트 층의 key. 선택 — 없으면 합성 시점에 검증한다
            String renderedKey,
            Double x,
            Double y,
            Double width,
            Double height,
            Double scale,
            Double rotation,
            // 과거 스웨거가 소문자 zindex로 노출한 이력 — 프론트가 아직 소문자도 보내서 alias로 받는다
            @JsonAlias("zindex") Integer zIndex,
            Map<String, Object> styleJson
    ) {

        // Jackson 3는 원시형에 누락/null을 채워주지 않는다 (FAIL_ON_NULL_FOR_PRIMITIVES 기본 on).
        // "안 보내면 0"이라는 계약을 언어 기본값에 기대지 않고 여기서 명시적으로 소유한다.
        // width/height/scale은 의도적으로 제외 — null("안 보냈다")을 보존하고 보정은 응답 조립에서 한다
        public ComponentRequest {
            x = x == null ? 0.0 : x;
            y = y == null ? 0.0 : y;
            rotation = rotation == null ? 0.0 : rotation;
            zIndex = zIndex == null ? 0 : zIndex;
        }
    }
}
