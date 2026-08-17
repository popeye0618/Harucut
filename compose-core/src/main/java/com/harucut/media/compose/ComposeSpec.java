package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// 합성 한 건의 자기 완결적 렌더 스펙 — 요청 시점의 프레임에서 파생해 Job에 스냅샷으로 저장한다.
// 프레임이 나중에 수정·삭제돼도 재실행은 이 스냅샷만 보므로, 사용자가 요청할 때 본 그대로 그려진다.
// 그리기 순서 계약(프론트 drawFrameOnce와 동일): 배경(색→이미지) → 원본 4장(슬롯, cover)
// → 레이어(zIndex 오름차순) → 셀 누끼 비네트
public record ComposeSpec(
        int canvasWidth,
        int canvasHeight,
        BackgroundAttributes background,
        List<FrameLayout.Slot> slots,
        List<Boolean> cellCutouts,
        List<Layer> layers
) {

    public ComposeSpec {
        slots = List.copyOf(slots);
        cellCutouts = List.copyOf(cellCutouts);
        layers = List.copyOf(layers);
    }

    // 스펙이 참조하는 이미지 자산 전부: IMAGE 배경 + 레이어 (중복 제거, 등장 순서 유지).
    // 서버 실행기와 Lambda 핸들러가 이 목록으로 내려받는다 — 계산이 두 벌이면 어긋난다
    public List<String> referencedAssetKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (background instanceof BackgroundAttributes.Image image) {
            keys.add(image.key());
        }
        for (Layer layer : layers) {
            keys.add(layer.source());
        }
        return List.copyOf(new ArrayList<>(keys));
    }

    // 프레임 컴포넌트에서 파생된 비트맵 층 하나. 텍스트는 이미 구운 이미지(renderedKey)로
    // 오므로 여기서는 전부 "이미지 + 위치"다 — 컴포넌트 타입 구분이 필요 없다.
    // 변환 의미는 프론트 drawThemeOverlay와 동일: 이미지를 width×height로 늘린 뒤
    // 중심 기준으로 scale·rotation(도)을 적용하고 opacity(styleJson에서 파생, 0~1)로 얹는다
    public record Layer(
            // S3 key — 합성기가 가져올 수 있는 참조여야 한다 (스펙 조립 시 보장. 정적 경로는 없다)
            String source,
            double x, double y,
            double width, double height,
            double scale,
            double rotation,
            int zIndex,
            double opacity
    ) {
    }
}
