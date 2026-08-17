package com.harucut.frame.enums;

import com.harucut.frame.layout.FrameLayout;
import com.harucut.frame.layout.FrameLayout.Slot;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

// 슬롯 좌표 출처: harucut_FE apps/web/constants/frameLayouts.ts (커밋 1e29c2b7).
// 프론트는 계산식, 여기는 대입 결과 리터럴 — 값이 다르면 합성 결과가 프론트 미리보기와 어긋난다
@Getter
@RequiredArgsConstructor
public enum FrameType {
    // 세로 스트립 1열 4칸
    CLASSIC(new FrameLayout(2000, 6000, List.of(
            new Slot(150, 200, 1700, 1200),
            new Slot(150, 1480, 1700, 1200),
            new Slot(150, 2760, 1700, 1200),
            new Slot(150, 4040, 1700, 1200)))),
    // 가로 2×2
    WIDE(new FrameLayout(6000, 4000, List.of(
            new Slot(200, 200, 2400, 1700),
            new Slot(2800, 200, 2400, 1700),
            new Slot(200, 2100, 2400, 1700),
            new Slot(2800, 2100, 2400, 1700)))),
    // 세로 2×2
    GRID(new FrameLayout(4000, 6000, List.of(
            new Slot(200, 200, 1700, 2400),
            new Slot(2100, 200, 1700, 2400),
            new Slot(200, 2800, 1700, 2400),
            new Slot(2100, 2800, 1700, 2400)))),
    // 오른쪽 열이 600px 내려앉은 지그재그
    POLAROID(new FrameLayout(4000, 6000, List.of(
            new Slot(200, 200, 1700, 2400),
            new Slot(2100, 800, 1700, 2400),
            new Slot(200, 2800, 1700, 2400),
            new Slot(2100, 3400, 1700, 2400))));

    private final FrameLayout layout;
}
