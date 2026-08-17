package com.harucut.frame.layout;

import java.util.List;

// 캔버스 크기와 촬영 사진 슬롯은 프론트 렌더러(harucut_FE apps/web/constants/frameLayouts.ts)와의
// 1:1 계약이다. 어긋나도 예외는 안 나고 합성 결과만 미리보기와 틀어진다.
// 서버가 진실의 원천 — 합성 스펙에 이 숫자를 그대로 실어 보내고, Lambda는 자체 상수를 갖지 않는다
public record FrameLayout(
        int canvasWidth,
        int canvasHeight,
        List<Slot> slots
) {

    // 촬영 사진 한 장이 들어가는 사각형 (출력 픽셀 기준). 목록 순서 = 촬영 순서
    public record Slot(int x, int y, int width, int height) {
    }
}
