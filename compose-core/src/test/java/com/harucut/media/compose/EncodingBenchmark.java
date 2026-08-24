package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout.Slot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;
import java.util.stream.Stream;

// 결과물 저장 포맷(PNG → JPEG q?)을 정하기 위한 측정. docs/perf-10k-dau.md §3.4의 ☐1-1·1-2·1-4·1-5.
//
// ⚠️ 이건 테스트가 아니다 — 단언이 없다. 통과/실패가 아니라 표를 찍는 게 전부다.
// 그래서 @Tag("bench")로 묶어 CI의 test에서 빼고, 전용 태스크로만 돈다:
//
//   ./gradlew :compose-core:benchmark
//   ./gradlew :compose-core:benchmark -Dbench.photos=C:/사진폴더   (실제 사진 4장을 한 세트 더)
//   ./gradlew :compose-core:benchmark -Dbench.dump=true            (결과물을 build/bench/에 저장)
//
// Lambda와는 무관하다. 여기서 재는 FourcutRenderer는 서버와 Lambda가 함께 쓰는 코드라
// AWS 없이 로컬에서 그대로 잴 수 있다 — compose-core가 순수 자바인 덕이다
@Tag("bench")
class EncodingBenchmark {

    // ── 측정 조건 ──────────────────────

    private static final float[] QUALITIES = {0.85f, 0.90f, 0.95f};

    // 재는 값이 수백 ms~수 초라 JIT 노이즈가 상대적으로 작다 — JMH 없이 이 정도면 충분하다.
    // 마이크로초를 잴 거면 이 방식은 못 쓴다
    private static final int WARMUP = 1;
    private static final int RUNS = 3;

    // ⚠️ 프론트가 몇 픽셀로 올리는지 아직 확인 안 됐다. 감사 문서(2026-08-23)가 쓴 픽스처 크기를
    // 그대로 써서 그쪽 숫자와 비교 가능하게 둔다. GRID 슬롯이 1700×2400이므로 1.7배 확대돼 그려진다
    private static final int SOURCE_WIDTH = 1000;
    private static final int SOURCE_HEIGHT = 1500;

    private static final String TEXT_LAYER_KEY = "bench/text-layer";

    // FrameType(루트 프로젝트)은 compose-core에서 안 보인다 — 같은 숫자를 리터럴로 둔다.
    // 픽셀 수가 2배 차이나는 두 극단만 잰다: CLASSIC 1,200만 / GRID 2,400만
    private static final Frame CLASSIC = new Frame("CLASSIC 2000x6000", 2000, 6000, List.of(
            new Slot(150, 200, 1700, 1200),
            new Slot(150, 1480, 1700, 1200),
            new Slot(150, 2760, 1700, 1200),
            new Slot(150, 4040, 1700, 1200)));

    private static final Frame GRID = new Frame("GRID 4000x6000", 4000, 6000, List.of(
            new Slot(200, 200, 1700, 2400),
            new Slot(2100, 200, 1700, 2400),
            new Slot(200, 2800, 1700, 2400),
            new Slot(2100, 2800, 1700, 2400)));

    private static final List<Frame> FRAMES = List.of(CLASSIC, GRID);

    private final FourcutRenderer renderer = new FourcutRenderer();

    // ── ☐1-1 (+ ☐1-5) 포맷별 크기·인코딩 시간 ──────────────────────

    @Test
    void 포맷별_크기와_시간() {
        markdown("1-1 포맷별 크기·인코딩 시간", "프레임", "입력", "포맷", "크기", "인코딩");

        for (Frame frame : FRAMES) {
            for (SourceSet sources : sourceSets()) {
                BufferedImage canvas = draw(frame, sources);
                // PNG는 ARGB 캔버스를 그대로 받지만 JPEG는 알파를 못 다룬다.
                // 평탄화를 인코딩 루프 밖에서 한 번만 — 안 그러면 q 비교에 평탄화 비용이 섞인다
                BufferedImage rgb = FourcutRenderer.toRgb(canvas);

                measure(frame, sources, "PNG", "png", () -> FourcutRenderer.encodePng(canvas));
                for (float quality : QUALITIES) {
                    measure(frame, sources, "JPEG q%.2f".formatted(quality), "jpg",
                            () -> FourcutRenderer.encodeJpeg(rgb, quality));
                }
            }
        }
    }

    // ── ☐1-2 ARGB→RGB 평탄화 비용 ──────────────────────

    // JPEG로 바꿔서 번 시간에서 이걸 빼야 정직한 숫자가 된다.
    // 지금은 썸네일 경로(drawScaled)만 우연히 RGB로 변환되고 원본에는 그 단계가 없다
    @Test
    void 평탄화_비용() {
        markdown("1-2 ARGB→RGB 평탄화 비용", "프레임", "입력", "평탄화");

        for (Frame frame : FRAMES) {
            for (SourceSet sources : sourceSets()) {
                BufferedImage canvas = draw(frame, sources);
                row(frame.label(), sources.label(),
                        medianMillis(() -> FourcutRenderer.toRgb(canvas)) + "ms");
            }
        }
    }

    // ── ☐1-4 렌더 전체에서 인코딩이 차지하는 비중 ──────────────────────

    // 인용값 "74%"를 자체 값으로 바꾼다
    @Test
    void 렌더_단계별_비중() {
        markdown("1-4 렌더 단계별 비중", "프레임", "입력", "그리기", "PNG 인코딩", "render 전체", "PNG 비중");

        for (Frame frame : FRAMES) {
            for (SourceSet sources : sourceSets()) {
                ComposeSpec spec = specFor(frame);
                Map<String, byte[]> assets = assetsFor(frame);
                List<byte[]> photos = sources.photos();

                long drawMillis = medianMillis(() -> renderer.draw(spec, photos, assets));
                BufferedImage canvas = renderer.draw(spec, photos, assets);
                long pngMillis = medianMillis(() -> FourcutRenderer.encodePng(canvas));
                long totalMillis = medianMillis(() -> renderer.render(spec, photos, assets));

                row(frame.label(), sources.label(), drawMillis + "ms", pngMillis + "ms",
                        totalMillis + "ms", "%.0f%%".formatted(100.0 * pngMillis / totalMillis));
            }
        }
    }

    // ── 측정 도구 ──────────────────────

    private void measure(Frame frame, SourceSet sources, String format, String extension,
                         Supplier<byte[]> encode) {
        long millis = medianMillis(encode::get);
        byte[] encoded = encode.get();
        row(frame.label(), sources.label(), format, megabytes(encoded.length), millis + "ms");
        dump(frame, sources, format, extension, encoded);
    }

    // 첫 실행은 JIT 컴파일 전이라 유독 느리다 — 몇 번 버리고 잰 뒤 중앙값을 쓴다.
    // 평균이 아니라 중앙값인 이유: GC가 한 번 끼면 평균이 통째로 끌려간다
    private static long medianMillis(Runnable work) {
        for (int i = 0; i < WARMUP; i++) {
            work.run();
        }
        long[] samples = new long[RUNS];
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            work.run();
            samples[i] = (System.nanoTime() - start) / 1_000_000;
        }
        Arrays.sort(samples);
        return samples[RUNS / 2];
    }

    // ── 픽스처: 캔버스 ──────────────────────

    private BufferedImage draw(Frame frame, SourceSet sources) {
        return renderer.draw(specFor(frame), sources.photos(), assetsFor(frame));
    }

    // 실제 최악 조건으로 잰다: 누끼 링(선명한 초록 테두리) + 구운 텍스트 층.
    // JPEG가 무너지는 자리는 사진 한가운데가 아니라 이런 경계다 — 빼고 재면 유리한 숫자만 나온다
    private static ComposeSpec specFor(Frame frame) {
        List<Boolean> cutouts = frame.slots().stream().map(slot -> true).toList();
        ComposeSpec.Layer text = new ComposeSpec.Layer(TEXT_LAYER_KEY,
                frame.width() * 0.08, frame.height() * 0.03,
                frame.width() * 0.84, frame.height() * 0.07,
                1.0, 0, 10, 1.0);
        return new ComposeSpec(frame.width(), frame.height(),
                new BackgroundAttributes.Color("#23262D"),
                frame.slots(), cutouts, List.of(text));
    }

    private static Map<String, byte[]> assetsFor(Frame frame) {
        return Map.of(TEXT_LAYER_KEY, textLayerPng(
                (int) Math.round(frame.width() * 0.84), (int) Math.round(frame.height() * 0.07)));
    }

    // 프론트가 구워 올리는 텍스트 층 흉내 — 투명 배경 + 흰 글자
    private static byte[] textLayerPng(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) (height * 0.7)));
            g.drawString("2026.08.24  HARUCUT", width * 0.03f, height * 0.75f);
        } finally {
            g.dispose();
        }
        return FourcutRenderer.encodePng(image);
    }

    // ── 픽스처: 원본 사진 ──────────────────────

    // 기본은 생성 이미지 3종. -Dbench.photos=<디렉터리>를 주면 그 안의 사진 4장이 한 세트 더 붙는다.
    //
    // ⚠️ 생성 이미지는 포맷 간 **상대 비교**에는 쓸 수 있지만 "우리 서비스 파일이 실제로 몇 MB냐"는
    // 못 말한다. 문서에 절대값을 적으려면 실제 사진을 물려서 나온 행을 써야 한다
    private List<SourceSet> sourceSets() {
        List<SourceSet> sets = new ArrayList<>();
        for (PhotoKind kind : PhotoKind.values()) {
            List<byte[]> photos = new ArrayList<>();
            for (int index = 0; index < 4; index++) {
                photos.add(photo(kind, index));
            }
            sets.add(new SourceSet(kind.label, photos));
        }
        realPhotos().ifPresent(sets::add);
        return sets;
    }

    private static Optional<SourceSet> realPhotos() {
        String directory = System.getProperty("bench.photos");
        if (directory == null || directory.isBlank()) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(Path.of(directory))) {
            List<byte[]> photos = files.filter(Files::isRegularFile).sorted()
                    .limit(4).map(EncodingBenchmark::readAll).toList();
            if (photos.size() != 4) {
                throw new IllegalStateException(
                        "bench.photos 디렉터리에 사진이 4장 있어야 한다: " + directory);
            }
            return Optional.of(new SourceSet("실사진", photos));
        } catch (IOException e) {
            throw new UncheckedIOException("bench.photos 를 읽을 수 없다: " + directory, e);
        }
    }

    private static byte[] readAll(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("사진을 읽을 수 없다: " + path, e);
        }
    }

    // 사진 비슷한 그림 = 낮은 주파수(부드러운 그라디언트) + 높은 주파수(픽셀 노이즈).
    // 두 포맷의 차이는 사실상 높은 주파수를 어떻게 다루느냐다 — PNG는 그걸 짊어지고 JPEG는 버린다.
    // FourcutRendererTest의 단색·반반 픽스처로는 그 차이가 아예 안 드러나서 재사용할 수 없다
    private static byte[] photo(PhotoKind kind, int index) {
        BufferedImage image =
                new BufferedImage(SOURCE_WIDTH, SOURCE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(index * 31L + kind.ordinal());   // 고정 시드 — 재현 가능해야 한다
        double phase = index * 0.7;

        for (int y = 0; y < SOURCE_HEIGHT; y++) {
            for (int x = 0; x < SOURCE_WIDTH; x++) {
                double u = (double) x / SOURCE_WIDTH;
                double v = (double) y / SOURCE_HEIGHT;
                int r = channel(120 + 110 * Math.sin(u * 3.1 + v * 1.7 + phase), kind.noise, random);
                int g = channel(110 + 100 * Math.sin(u * 2.3 - v * 2.9 + phase), kind.noise, random);
                int b = channel(130 + 90 * Math.cos(u * 1.9 + v * 3.4 + phase), kind.noise, random);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        if (kind == PhotoKind.EDGES) {
            drawSharpEdges(image);
        }
        return FourcutRenderer.encodePng(image);
    }

    private static int channel(double base, int noise, Random random) {
        double value = noise == 0 ? base : base + random.nextGaussian() * noise;
        return Math.min(255, Math.max(0, (int) Math.round(value)));
    }

    private static void drawSharpEdges(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, SOURCE_HEIGHT / 12));
            g.drawString("HARUCUT 2026", SOURCE_WIDTH / 20f, SOURCE_HEIGHT / 2f);
            g.setStroke(new BasicStroke(2f));
            for (int i = 1; i < 16; i++) {
                g.drawLine(0, SOURCE_HEIGHT * i / 16, SOURCE_WIDTH, SOURCE_HEIGHT * i / 16);
            }
        } finally {
            g.dispose();
        }
    }

    // ── 출력 ──────────────────────

    // 마크다운으로 찍는 이유: 이 측정의 목적이 docs/perf-10k-dau.md §3.4를 채우는 것이다.
    // 콘솔 숫자를 사람이 손으로 옮기면 오타가 난다
    private static void markdown(String title, String... headers) {
        System.out.printf("%n### %s%n%n", title);
        System.out.println("| " + String.join(" | ", headers) + " |");
        System.out.println("|" + "---|".repeat(headers.length));
    }

    private static void row(String... cells) {
        System.out.println("| " + String.join(" | ", cells) + " |");
    }

    private static String megabytes(int bytes) {
        return "%.2fMB".formatted(bytes / 1024.0 / 1024.0);
    }

    // ☐1-5 눈으로 확인할 파일. build/ 아래라 커밋되지 않는다.
    // 볼 때는 100% 확대해서 글자 테두리와 누끼 링을 봐야 한다 — 축소하면 q0.5도 멀쩡해 보인다
    private static void dump(Frame frame, SourceSet sources, String format, String extension,
                             byte[] bytes) {
        if (!Boolean.getBoolean("bench.dump")) {
            return;
        }
        try {
            Path directory = Path.of("build", "bench");
            Files.createDirectories(directory);
            Files.write(directory.resolve("%s-%s-%s.%s".formatted(
                    frame.label().split(" ")[0], sources.label(),
                    format.replace(" ", ""), extension)), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("벤치 산출물을 저장할 수 없다", e);
        }
    }

    // ── 타입 ──────────────────────

    private record Frame(String label, int width, int height, List<Slot> slots) {
    }

    private record SourceSet(String label, List<byte[]> photos) {
    }

    // 압축률은 그림 내용이 정한다 — 감사에서 같은 코드가 42.4MB와 14.4MB로 갈렸다(3배).
    // 한 세트로 q를 정하면 그 사진에만 맞는 값이 나온다
    private enum PhotoKind {
        NOISY("노이즈강", 40),    // 어두운 실내·고ISO — PNG 최악 조건
        SMOOTH("매끈", 2),        // 밝은 야외·단순 배경 — PNG 최선 조건
        EDGES("경계", 6);         // 선명한 글자·선 — JPEG가 유일하게 약한 조건

        private final String label;
        private final int noise;

        PhotoKind(String label, int noise) {
            this.label = label;
            this.noise = noise;
        }
    }
}
