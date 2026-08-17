package com.harucut.frame.dto;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.entity.FrameComponent;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;

import java.util.List;
import java.util.Map;

public record FrameResponse(
        Long frameId,
        String title,
        String description,
        String source,           // 프리뷰 presigned URL
        FrameType frameType,
        int canvasWidth,         // frameType 파생 고정값 — 요청이 뭐라 보냈든 무관
        int canvasHeight,
        BackgroundAttributes background,
        List<ComponentResponse> components,
        boolean isSystem
) {

    // presign이 필요한 조립은 어셈블러 몫이고, 이 팩토리는 캔버스 파생만 강제한다 —
    // 캔버스에 다른 값을 넣는 코드를 쓸 수 없게 파라미터에서 뺐다
    public static FrameResponse of(Frame frame, String previewUrl,
                                   BackgroundAttributes resolvedBackground,
                                   List<ComponentResponse> components) {
        return new FrameResponse(
                frame.getId(), frame.getTitle(), frame.getDescription(),
                previewUrl, frame.getFrameType(),
                frame.getFrameType().getLayout().canvasWidth(),
                frame.getFrameType().getLayout().canvasHeight(),
                resolvedBackground, components, frame.isSystem());
    }

    public record ComponentResponse(
            Long id,
            ComponentType type,
            String source,       // 관리 대상 PHOTO면 presigned URL, 아니면 원본
            String key,          // 순수 key — 프론트가 수정 요청을 다시 만들 때 쓴다
            double x, double y,
            double width, double height,   // 저장이 null이었으면 0.0
            double scale,                  // 저장이 null이었으면 1.0
            double rotation,
            int zIndex,
            Map<String, Object> style
    ) {

        // null 보정 규칙의 소유자 — "안 보냈다"는 기록(null)을 응답 계약(0.0/1.0)으로 번역한다
        public static ComponentResponse of(FrameComponent component, String resolvedSource) {
            return new ComponentResponse(
                    component.getId(), component.getType(), resolvedSource, component.getSource(),
                    component.getX(), component.getY(),
                    component.getWidth() == null ? 0.0 : component.getWidth(),
                    component.getHeight() == null ? 0.0 : component.getHeight(),
                    component.getScale() == null ? 1.0 : component.getScale(),
                    component.getRotation(), component.getZIndex(), component.getStyle());
        }
    }
}
