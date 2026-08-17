package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout.Slot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 픽셀 테스트 — 작은 캔버스에 그려서 특정 좌표의 색으로 그리기 규칙을 고정한다.
// 경계(안티앨리어싱) 위가 아니라 영역 한가운데를 찍는다
@DisplayName("FourcutRenderer")
class FourcutRendererTest {

    private static final Color WHITE = new Color(255, 255, 255);
    private static final Color RED = new Color(255, 0, 0);
    private static final Color GREEN = new Color(0, 255, 0);
    private static final Color BLUE = new Color(0, 0, 255);
    private static final Color YELLOW = new Color(255, 255, 0);

    private final FourcutRenderer renderer = new FourcutRenderer();

    @Nested
    @DisplayName("배경")
    class Background {

        @Test
        @DisplayName("COLOR 배경이 캔버스 크기 전체를 칠한다")
        void colorFillsCanvas() {
            BufferedImage result = decode(renderer.render(
                    spec(new BackgroundAttributes.Color("#FF0000"), List.of(), List.of(), List.of()),
                    List.of(), Map.of()));

            assertThat(result.getWidth()).isEqualTo(100);
            assertThat(result.getHeight()).isEqualTo(100);
            assertThat(pixel(result, 50, 50)).isEqualTo(RED);
        }

        @Test
        @DisplayName("#RGB 축약형은 #RRGGBB로 확장된다 — 프론트 normalizeHexColor와 동일")
        void shortHexExpanded() {
            BufferedImage result = decode(renderer.render(
                    spec(new BackgroundAttributes.Color("#0F0"), List.of(), List.of(), List.of()),
                    List.of(), Map.of()));

            assertThat(pixel(result, 50, 50)).isEqualTo(GREEN);
        }

        @Test
        @DisplayName("IMAGE 배경은 기본색 #23262D 위에 불투명도를 적용해 깔린다")
        void imageBackgroundBlendsOverBase() {
            ComposeSpec spec = spec(new BackgroundAttributes.Image("bg-key", 0.5, null),
                    List.of(), List.of(), List.of());

            BufferedImage result = decode(renderer.render(
                    spec, List.of(), Map.of("bg-key", solidPng(10, 10, RED))));

            // 빨강 50% + 기본색(35,38,45) 50%
            assertCloseTo(pixel(result, 50, 50), new Color(145, 19, 22), 3);
        }
    }

    @Nested
    @DisplayName("원본 4장 — 슬롯에 cover")
    class SourcePhotos {

        @Test
        @DisplayName("원본이 슬롯 순서대로 각자의 칸에 그려진다")
        void photosLandInTheirSlots() {
            ComposeSpec spec = spec(whiteBackground(),
                    List.of(new Slot(5, 5, 40, 40), new Slot(55, 5, 40, 40),
                            new Slot(5, 55, 40, 40), new Slot(55, 55, 40, 40)),
                    List.of(), List.of());

            BufferedImage result = decode(renderer.render(spec, List.of(
                    solidPng(10, 10, RED), solidPng(10, 10, GREEN),
                    solidPng(10, 10, BLUE), solidPng(10, 10, YELLOW)), Map.of()));

            assertThat(pixel(result, 25, 25)).isEqualTo(RED);
            assertThat(pixel(result, 75, 25)).isEqualTo(GREEN);
            assertThat(pixel(result, 25, 75)).isEqualTo(BLUE);
            assertThat(pixel(result, 75, 75)).isEqualTo(YELLOW);
        }

        @Test
        @DisplayName("cover에서 넘치는 부분은 슬롯 밖으로 나가지 않는다 — 클리핑")
        void overflowClippedToSlot() {
            // 40x10 가로 이미지를 20x20 슬롯에 → 확대 후 좌우가 슬롯 밖으로 넘친다
            ComposeSpec spec = spec(whiteBackground(),
                    List.of(new Slot(40, 40, 20, 20)), List.of(), List.of());

            BufferedImage result = decode(renderer.render(
                    spec, List.of(solidPng(40, 10, BLUE)), Map.of()));

            assertThat(pixel(result, 50, 50)).isEqualTo(BLUE);
            assertThat(pixel(result, 30, 50)).isEqualTo(WHITE);
            assertThat(pixel(result, 70, 50)).isEqualTo(WHITE);
        }

        @Test
        @DisplayName("cover는 가운데 정렬이다 — 잘리는 건 양쪽 끝이다")
        void coverIsCenterAligned() {
            // 왼쪽 절반 빨강, 오른쪽 절반 파랑인 20x10 이미지 → 정사각 슬롯 중앙에 색 경계가 온다
            ComposeSpec spec = spec(whiteBackground(),
                    List.of(new Slot(40, 40, 20, 20)), List.of(), List.of());

            BufferedImage result = decode(renderer.render(
                    spec, List.of(twoTonePng(20, 10, RED, BLUE)), Map.of()));

            assertCloseTo(pixel(result, 44, 50), RED, 3);
            assertCloseTo(pixel(result, 56, 50), BLUE, 3);
        }
    }

    @Nested
    @DisplayName("레이어 — 스티커·구운 텍스트")
    class Layers {

        @Test
        @DisplayName("겹치면 zIndex가 큰 레이어가 위다 — 목록 순서가 아니라 zIndex 순서")
        void higherZIndexWins() {
            // 목록에는 위(z=2)가 먼저 온다 — 정렬하지 않으면 green이 위가 되는 배치
            ComposeSpec spec = spec(whiteBackground(), List.of(), List.of(), List.of(
                    layer("red", 30, 10, 30, 30, 0, 2, 1.0),
                    layer("green", 10, 10, 30, 30, 0, 1, 1.0)));

            BufferedImage result = decode(renderer.render(spec, List.of(), Map.of(
                    "red", solidPng(10, 10, RED), "green", solidPng(10, 10, GREEN))));

            assertThat(pixel(result, 35, 25)).isEqualTo(RED);
            assertThat(pixel(result, 15, 25)).isEqualTo(GREEN);
        }

        @Test
        @DisplayName("회전은 중심 기준이다 — 45도 돌리면 원래 모서리 자리가 빈다")
        void rotationAboutCenter() {
            ComposeSpec spec = spec(whiteBackground(), List.of(), List.of(), List.of(
                    layer("red", 30, 30, 40, 40, 45, 1, 1.0)));

            BufferedImage result = decode(renderer.render(
                    spec, List.of(), Map.of("red", solidPng(10, 10, RED))));

            assertCloseTo(pixel(result, 50, 50), RED, 3);
            assertCloseTo(pixel(result, 33, 33), WHITE, 3);
        }

        @Test
        @DisplayName("opacity 0.5인 레이어는 배경과 반씩 섞인다")
        void opacityBlends() {
            ComposeSpec spec = spec(whiteBackground(), List.of(), List.of(), List.of(
                    layer("red", 30, 30, 40, 40, 0, 1, 0.5)));

            BufferedImage result = decode(renderer.render(
                    spec, List.of(), Map.of("red", solidPng(10, 10, RED))));

            assertCloseTo(pixel(result, 50, 50), new Color(255, 128, 128), 3);
        }
    }

    @Nested
    @DisplayName("셀 누끼 비네트 — 최상단 후처리")
    class CellCutouts {

        @Test
        @DisplayName("켠 칸은 중앙이 밝고 가장자리로 갈수록 어두워진다")
        void vignetteDarkensEdges() {
            BufferedImage result = decode(renderer.render(cutoutSpec(),
                    List.of(solidPng(10, 10, WHITE)), Map.of()));

            assertCloseTo(pixel(result, 100, 100), WHITE, 5);
            assertThat(pixel(result, 40, 40).getRed()).isLessThan(200);
        }

        @Test
        @DisplayName("켠 칸의 테두리에 녹색 링이 그려진다")
        void greenRingOnSlotBorder() {
            BufferedImage result = decode(renderer.render(cutoutSpec(),
                    List.of(solidPng(10, 10, WHITE)), Map.of()));

            assertCloseTo(pixel(result, 100, 20), new Color(0x1E, 0xD7, 0x60), 40);
        }

        // 200x200 캔버스에 160x160 슬롯 하나, 누끼 켬
        private ComposeSpec cutoutSpec() {
            return new ComposeSpec(200, 200, whiteBackground(),
                    List.of(new Slot(20, 20, 160, 160)), List.of(true), List.of());
        }
    }

    @Nested
    @DisplayName("썸네일 — 원본과 함께 나오는 축소본")
    class Thumbnail {

        @Test
        @DisplayName("세로형(600x1800)은 긴 변이 512로 줄고 비율이 유지된다 — 171x512")
        void portraitScaledToLongEdge() {
            RenderResult result = renderer.render(
                    new ComposeSpec(600, 1800, new BackgroundAttributes.Color("#FF0000"),
                            List.of(), List.of(), List.of()),
                    List.of(), Map.of());

            BufferedImage thumb = decode(result.thumbnailJpeg());
            assertThat(thumb.getHeight()).isEqualTo(512);
            assertThat(thumb.getWidth()).isEqualTo(171);   // 600 × (512/1800) 반올림
        }

        @Test
        @DisplayName("가로형(1200x400)은 512x171이다 — 절반 축소를 한 번 거치는 경로")
        void landscapeScaledThroughHalving() {
            RenderResult result = renderer.render(
                    new ComposeSpec(1200, 400, new BackgroundAttributes.Color("#FF0000"),
                            List.of(), List.of(), List.of()),
                    List.of(), Map.of());

            BufferedImage thumb = decode(result.thumbnailJpeg());
            assertThat(thumb.getWidth()).isEqualTo(512);
            assertThat(thumb.getHeight()).isEqualTo(171);
        }

        @Test
        @DisplayName("원본이 512보다 작으면 확대하지 않는다 — 100x100 그대로")
        void neverUpscaled() {
            RenderResult result = renderer.render(
                    spec(whiteBackground(), List.of(), List.of(), List.of()),
                    List.of(), Map.of());

            BufferedImage thumb = decode(result.thumbnailJpeg());
            assertThat(thumb.getWidth()).isEqualTo(100);
            assertThat(thumb.getHeight()).isEqualTo(100);
        }

        @Test
        @DisplayName("썸네일은 JPEG이다 — 매직 바이트 FF D8")
        void thumbnailIsJpeg() {
            RenderResult result = renderer.render(
                    spec(whiteBackground(), List.of(), List.of(), List.of()),
                    List.of(), Map.of());

            assertThat(result.thumbnailJpeg()[0]).isEqualTo((byte) 0xFF);
            assertThat(result.thumbnailJpeg()[1]).isEqualTo((byte) 0xD8);
        }

        @Test
        @DisplayName("축소 후에도 내용이 같은 상대 위치에 있다 (JPEG 손실 감안 ±8)")
        void contentSurvivesScaling() {
            // 1024 캔버스 왼쪽 절반이 파랑 슬롯 → 512 썸네일에서도 왼쪽 절반이 파랑
            ComposeSpec spec = new ComposeSpec(1024, 1024, whiteBackground(),
                    List.of(new Slot(0, 0, 512, 1024)), List.of(), List.of());

            RenderResult result = renderer.render(spec, List.of(solidPng(10, 10, BLUE)), Map.of());

            BufferedImage thumb = decode(result.thumbnailJpeg());
            assertThat(thumb.getWidth()).isEqualTo(512);
            assertCloseTo(pixel(thumb, 128, 256), BLUE, 8);
            assertCloseTo(pixel(thumb, 384, 256), WHITE, 8);
        }
    }

    @Nested
    @DisplayName("입력 검증 — 조용히 엉킨 그림 대신 예외")
    class InputValidation {

        @Test
        @DisplayName("원본 수가 슬롯 수와 다르면 거부한다")
        void sourceCountMismatch() {
            ComposeSpec spec = spec(whiteBackground(),
                    List.of(new Slot(5, 5, 40, 40), new Slot(55, 5, 40, 40)),
                    List.of(), List.of());

            assertThatThrownBy(() -> renderer.render(spec, List.of(solidPng(10, 10, RED)), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("스펙이 참조하는 이미지가 assets에 없으면 어떤 key인지 말하고 거부한다")
        void missingAssetNamed() {
            ComposeSpec spec = spec(whiteBackground(), List.of(), List.of(), List.of(
                    layer("missing-key", 0, 0, 10, 10, 0, 1, 1.0)));

            assertThatThrownBy(() -> renderer.render(spec, List.of(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("missing-key");
        }

        @Test
        @DisplayName("이미지가 아닌 바이트는 거부한다")
        void undecodableBytesRejected() {
            ComposeSpec spec = spec(whiteBackground(),
                    List.of(new Slot(5, 5, 40, 40)), List.of(), List.of());

            assertThatThrownBy(() -> renderer.render(
                    spec, List.of("이미지 아님".getBytes()), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── fixtures ──────────────────────────────

    private static ComposeSpec spec(BackgroundAttributes background, List<Slot> slots,
                                    List<Boolean> cutouts, List<ComposeSpec.Layer> layers) {
        return new ComposeSpec(100, 100, background, slots, cutouts, layers);
    }

    private static BackgroundAttributes whiteBackground() {
        return new BackgroundAttributes.Color("#FFFFFF");
    }

    private static ComposeSpec.Layer layer(String source, double x, double y,
                                           double width, double height,
                                           double rotation, int zIndex, double opacity) {
        return new ComposeSpec.Layer(source, x, y, width, height, 1.0, rotation, zIndex, opacity);
    }

    private static byte[] solidPng(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return encode(image);
    }

    private static byte[] twoTonePng(int width, int height, Color left, Color right) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(left);
        g.fillRect(0, 0, width / 2, height);
        g.setColor(right);
        g.fillRect(width / 2, 0, width - width / 2, height);
        g.dispose();
        return encode(image);
    }

    private static byte[] encode(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // 기존 픽셀 테스트는 전부 원본을 본다 — 썸네일 검증은 Thumbnail 클래스가 따로 한다
    private static BufferedImage decode(RenderResult result) {
        return decode(result.fullPng());
    }

    private static Color pixel(BufferedImage image, int x, int y) {
        return new Color(image.getRGB(x, y));
    }

    private static void assertCloseTo(Color actual, Color expected, int tolerance) {
        assertThat(Math.abs(actual.getRed() - expected.getRed()))
                .as("red (actual=%s, expected=%s)", actual, expected)
                .isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getGreen() - expected.getGreen()))
                .as("green (actual=%s, expected=%s)", actual, expected)
                .isLessThanOrEqualTo(tolerance);
        assertThat(Math.abs(actual.getBlue() - expected.getBlue()))
                .as("blue (actual=%s, expected=%s)", actual, expected)
                .isLessThanOrEqualTo(tolerance);
    }
}
