package com.harucut.frame.enums;

import com.harucut.frame.layout.FrameLayout;
import com.harucut.frame.layout.FrameLayout.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 캔버스 크기와 슬롯 좌표는 프론트 apps/web/constants/frameLayouts.ts와의 1:1 계약이다.
// 어긋나면 예외 없이 합성 결과만 미리보기와 틀어지므로, 기대값은 구현이 아니라 리터럴로 고정한다
@DisplayName("FrameType")
class FrameTypeTest {

    @Test
    @DisplayName("CLASSIC은 2000x6000 캔버스에 세로 1열 4칸이다")
    void classicLayout() {
        assertThat(FrameType.CLASSIC.getLayout()).isEqualTo(new FrameLayout(2000, 6000, List.of(
                new Slot(150, 200, 1700, 1200),
                new Slot(150, 1480, 1700, 1200),
                new Slot(150, 2760, 1700, 1200),
                new Slot(150, 4040, 1700, 1200))));
    }

    @Test
    @DisplayName("WIDE는 6000x4000 캔버스에 가로 2x2다")
    void wideLayout() {
        assertThat(FrameType.WIDE.getLayout()).isEqualTo(new FrameLayout(6000, 4000, List.of(
                new Slot(200, 200, 2400, 1700),
                new Slot(2800, 200, 2400, 1700),
                new Slot(200, 2100, 2400, 1700),
                new Slot(2800, 2100, 2400, 1700))));
    }

    @Test
    @DisplayName("GRID는 4000x6000 캔버스에 세로 2x2다")
    void gridLayout() {
        assertThat(FrameType.GRID.getLayout()).isEqualTo(new FrameLayout(4000, 6000, List.of(
                new Slot(200, 200, 1700, 2400),
                new Slot(2100, 200, 1700, 2400),
                new Slot(200, 2800, 1700, 2400),
                new Slot(2100, 2800, 1700, 2400))));
    }

    @Test
    @DisplayName("POLAROID는 4000x6000 캔버스에 오른쪽 열이 600px 내려앉은 지그재그다")
    void polaroidLayout() {
        assertThat(FrameType.POLAROID.getLayout()).isEqualTo(new FrameLayout(4000, 6000, List.of(
                new Slot(200, 200, 1700, 2400),
                new Slot(2100, 800, 1700, 2400),
                new Slot(200, 2800, 1700, 2400),
                new Slot(2100, 3400, 1700, 2400))));
    }

    @Test
    @DisplayName("모든 타입의 슬롯은 정확히 4개고 캔버스 경계 안에 있다")
    void slotsWithinCanvas() {
        for (FrameType type : FrameType.values()) {
            FrameLayout layout = type.getLayout();
            assertThat(layout.slots()).as("%s 슬롯 수", type).hasSize(4);
            for (Slot slot : layout.slots()) {
                assertThat(slot.x() + slot.width())
                        .as("%s 슬롯 %s 가로", type, slot).isLessThanOrEqualTo(layout.canvasWidth());
                assertThat(slot.y() + slot.height())
                        .as("%s 슬롯 %s 세로", type, slot).isLessThanOrEqualTo(layout.canvasHeight());
            }
        }
    }

    @Test
    @DisplayName("프레임 타입은 정확히 네 가지다 — 추가하면 프론트 상수와의 계약부터 맞춘다")
    void exactlyFourTypes() {
        assertThat(FrameType.values()).containsExactly(
                FrameType.CLASSIC, FrameType.WIDE, FrameType.GRID, FrameType.POLAROID);
    }
}
