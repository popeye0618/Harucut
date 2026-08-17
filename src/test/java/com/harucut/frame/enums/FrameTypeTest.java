package com.harucut.frame.enums;

import com.harucut.frame.layout.FrameLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// 캔버스 크기는 프론트 apps/web/constants/frameLayouts.ts와의 1:1 계약이다.
// 어긋나면 예외 없이 저장된 컴포넌트 좌표만 틀어지므로, 기대값은 구현이 아니라 리터럴로 고정한다.
@DisplayName("FrameType")
class FrameTypeTest {

    @Test
    @DisplayName("CLASSIC의 캔버스는 2000x6000이다")
    void classicCanvas() {
        assertThat(FrameType.CLASSIC.getLayout()).isEqualTo(new FrameLayout(2000, 6000));
    }

    @Test
    @DisplayName("WIDE의 캔버스는 6000x4000이다")
    void wideCanvas() {
        assertThat(FrameType.WIDE.getLayout()).isEqualTo(new FrameLayout(6000, 4000));
    }

    @Test
    @DisplayName("GRID의 캔버스는 4000x6000이다")
    void gridCanvas() {
        assertThat(FrameType.GRID.getLayout()).isEqualTo(new FrameLayout(4000, 6000));
    }

    @Test
    @DisplayName("POLAROID의 캔버스는 4000x6000이다")
    void polaroidCanvas() {
        assertThat(FrameType.POLAROID.getLayout()).isEqualTo(new FrameLayout(4000, 6000));
    }

    @Test
    @DisplayName("프레임 타입은 정확히 네 가지다 — 추가하면 프론트 상수와의 계약부터 맞춘다")
    void exactlyFourTypes() {
        assertThat(FrameType.values()).containsExactly(
                FrameType.CLASSIC, FrameType.WIDE, FrameType.GRID, FrameType.POLAROID);
    }
}
