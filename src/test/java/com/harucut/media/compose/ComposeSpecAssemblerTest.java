package com.harucut.media.compose;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.entity.FrameComponent;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ComposeSpecAssembler")
class ComposeSpecAssemblerTest {

    private static final String PHOTO_KEY = "uploads/users/abc/components/photo.png";
    private static final String STICKER_KEY = "uploads/users/abc/components/sticker.png";
    private static final String RENDERED_KEY = "uploads/users/abc/components/text.png";

    private final ComposeSpecAssembler assembler = new ComposeSpecAssembler();

    @Nested
    @DisplayName("프레임 → 스펙 변환")
    class Assemble {

        @Test
        @DisplayName("캔버스·슬롯은 frameType에서, cellCutouts는 프레임에서 온다")
        void canvasSlotsCutoutsMapped() {
            Frame frame = Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                    new BackgroundAttributes.Color("#FFE4E1"),
                    List.of(true, false, true, false));

            ComposeSpec spec = assembler.assemble(frame);

            assertThat(spec.canvasWidth()).isEqualTo(2000);
            assertThat(spec.canvasHeight()).isEqualTo(6000);
            assertThat(spec.slots()).isEqualTo(FrameType.CLASSIC.getLayout().slots());
            assertThat(spec.cellCutouts()).containsExactly(true, false, true, false);
        }

        @Test
        @DisplayName("PHOTO·STICKER는 source가, TEXT는 renderedKey가 레이어 참조가 된다")
        void layerSourcesByType() {
            Frame frame = colorFrame();
            frame.addComponent(component(ComponentType.PHOTO, PHOTO_KEY, null));
            frame.addComponent(component(ComponentType.STICKER, STICKER_KEY, null));
            frame.addComponent(component(ComponentType.TEXT, "봄 여행", RENDERED_KEY));

            ComposeSpec spec = assembler.assemble(frame);

            assertThat(spec.layers()).extracting(ComposeSpec.Layer::source)
                    .containsExactly(PHOTO_KEY, STICKER_KEY, RENDERED_KEY);
        }

        @Test
        @DisplayName("null 보정: width/height는 0, scale·opacity는 1이 된다")
        void nullDefaults() {
            Frame frame = colorFrame();
            frame.addComponent(FrameComponent.builder()
                    .source(PHOTO_KEY).type(ComponentType.PHOTO).build());

            ComposeSpec.Layer layer = assembler.assemble(frame).layers().get(0);

            assertThat(layer.width()).isZero();
            assertThat(layer.height()).isZero();
            assertThat(layer.scale()).isEqualTo(1.0);
            assertThat(layer.opacity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("styleJson.opacity가 레이어에 실리고 0~1로 잘린다")
        void opacityFromStyleClamped() {
            Frame frame = colorFrame();
            frame.addComponent(FrameComponent.builder()
                    .source(PHOTO_KEY).type(ComponentType.PHOTO)
                    .style(Map.of("opacity", 0.4)).build());
            frame.addComponent(FrameComponent.builder()
                    .source(STICKER_KEY).type(ComponentType.STICKER)
                    .style(Map.of("opacity", 1.5)).build());

            List<ComposeSpec.Layer> layers = assembler.assemble(frame).layers();

            assertThat(layers.get(0).opacity()).isEqualTo(0.4);
            assertThat(layers.get(1).opacity()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("합성 가능성 관문 — S3에 없는 참조는 400")
    class ComposabilityGate {

        @Test
        @DisplayName("renderedKey 없는 TEXT는 GEN-002다 — 1단계에서 미뤄둔 검증의 자리")
        void unbakedTextRejected() {
            Frame frame = colorFrame();
            frame.addComponent(component(ComponentType.TEXT, "안 구운 텍스트", null));

            assertThatThrownBy(() -> assembler.assemble(frame))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("관리 key가 아닌 스티커(외부 URL)는 GEN-002다")
        void externalStickerRejected() {
            Frame frame = colorFrame();
            frame.addComponent(component(ComponentType.STICKER,
                    "https://cdn.example.com/stickers/heart.png", null));

            assertThatThrownBy(() -> assembler.assemble(frame))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("관리 key가 아닌 IMAGE 배경도 GEN-002다")
        void externalBackgroundRejected() {
            Frame frame = Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                    new BackgroundAttributes.Image("https://cdn.example.com/bg.png", 0.8, null));

            assertThatThrownBy(() -> assembler.assemble(frame))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("관리 key인 IMAGE 배경은 그대로 통과한다")
        void managedBackgroundPasses() {
            Frame frame = Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                    new BackgroundAttributes.Image("uploads/users/abc/components/bg.png", 0.8, null));

            ComposeSpec spec = assembler.assemble(frame);

            assertThat(spec.background()).isEqualTo(
                    new BackgroundAttributes.Image("uploads/users/abc/components/bg.png", 0.8, null));
        }
    }

    // ── fixtures ──────────────────────────────

    private static Frame colorFrame() {
        return Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                new BackgroundAttributes.Color("#FFE4E1"));
    }

    private static FrameComponent component(ComponentType type, String source, String renderedKey) {
        return FrameComponent.builder()
                .source(source).type(type).renderedKey(renderedKey)
                .x(120.5).y(220.0).width(360.0).height(480.0)
                .scale(1.0).rotation(45.0).zIndex(3)
                .build();
    }
}
