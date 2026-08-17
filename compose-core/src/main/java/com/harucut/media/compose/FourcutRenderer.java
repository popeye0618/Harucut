package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// 스펙과 이미지 바이트를 받아 완성된 네컷 PNG를 만드는 순수 그리기 부품.
// S3를 모른다 — 이미지 가져오기(다운로드)와 결과 내보내기(업로드)는 호출자 몫이다.
// 그래서 픽셀 테스트가 S3 없이 돌고, 이 코드가 그대로 Lambda 함수 안으로 들어간다.
// 수식·순서·상수는 프론트 composeFrame.ts(drawFrameOnce)와 1:1이다 —
// 어긋나면 예외 없이 결과물만 편집 화면 미리보기와 틀어진다.
// 공유 모듈(compose-core) 소속이라 스프링 애노테이션이 없다 — 빈 등록은 앱의 ComposeConfig 몫
public class FourcutRenderer {

    // 프론트 DEFAULT_FRAME_BACKGROUND_COLOR — IMAGE 배경 아래에 깔리는 기본색
    private static final Color IMAGE_BACKGROUND_BASE = new Color(0x23, 0x26, 0x2D);

    // 셀 누끼 비네트 상수 — 프론트 drawCellCutouts와 동일
    private static final double VIGNETTE_RADIUS_RATIO = 0.62;
    private static final float VIGNETTE_INNER_STOP = 0.6f;
    private static final Color VIGNETTE_CENTER = new Color(0, 0, 0, 0);
    private static final Color VIGNETTE_EDGE = new Color(11, 11, 12, Math.round(0.82f * 255));
    // 프론트 traceRoundedRect의 r=40 — 작은 슬롯에선 변 절반까지로 클램프된다
    private static final double CUTOUT_CORNER_RADIUS = 40;
    private static final float CUTOUT_RING_WIDTH = 10f;
    private static final Color CUTOUT_RING = new Color(0x1E, 0xD7, 0x60);

    public byte[] render(ComposeSpec spec, List<byte[]> sourcePhotos, Map<String, byte[]> assets) {
        if (sourcePhotos == null || sourcePhotos.size() != spec.slots().size()) {
            throw new IllegalArgumentException("원본 사진 수가 슬롯 수와 다르다");
        }

        BufferedImage canvas = new BufferedImage(
                spec.canvasWidth(), spec.canvasHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            applyQualityHints(g);
            drawBackground(g, spec, assets);
            drawSourcePhotos(g, spec, sourcePhotos);
            drawLayers(g, spec, assets);
            drawCellCutouts(g, spec);
        } finally {
            g.dispose();
        }
        return encodePng(canvas);
    }

    // ── 그리기 순서 1: 배경 (색 → 이미지) ──────────────────────

    private void drawBackground(Graphics2D g, ComposeSpec spec, Map<String, byte[]> assets) {
        Rectangle2D whole = new Rectangle2D.Double(0, 0, spec.canvasWidth(), spec.canvasHeight());
        switch (spec.background()) {
            case BackgroundAttributes.Color color -> {
                g.setColor(parseHexColor(color.value()));
                g.fill(whole);
            }
            case BackgroundAttributes.Image image -> {
                g.setColor(IMAGE_BACKGROUND_BASE);
                g.fill(whole);
                BufferedImage bg = decode(requireAsset(assets, image.key()));
                Composite old = g.getComposite();
                g.setComposite(AlphaComposite.getInstance(
                        AlphaComposite.SRC_OVER, (float) clamp01(image.opacity())));
                drawCover(g, bg, whole);
                g.setComposite(old);
            }
        }
    }

    // ── 그리기 순서 2: 원본 4장을 슬롯에 cover로 ──────────────────────

    private void drawSourcePhotos(Graphics2D g, ComposeSpec spec, List<byte[]> sourcePhotos) {
        List<FrameLayout.Slot> slots = spec.slots();
        for (int i = 0; i < slots.size(); i++) {
            FrameLayout.Slot slot = slots.get(i);
            drawCover(g, decode(sourcePhotos.get(i)),
                    new Rectangle2D.Double(slot.x(), slot.y(), slot.width(), slot.height()));
        }
    }

    // ── 그리기 순서 3: 레이어(스티커·구운 텍스트)를 zIndex 오름차순으로 ──────────────────────

    private void drawLayers(Graphics2D g, ComposeSpec spec, Map<String, byte[]> assets) {
        List<ComposeSpec.Layer> ordered = spec.layers().stream()
                .sorted(Comparator.comparingInt(ComposeSpec.Layer::zIndex))
                .toList();

        for (ComposeSpec.Layer layer : ordered) {
            BufferedImage image = decode(requireAsset(assets, layer.source()));
            AffineTransform oldTransform = g.getTransform();
            Composite oldComposite = g.getComposite();

            // 프론트와 같은 변환: 중심으로 이동 → 회전 → 확대 → 좌상단으로 복귀
            g.translate(layer.x() + layer.width() / 2, layer.y() + layer.height() / 2);
            g.rotate(Math.toRadians(layer.rotation()));
            g.scale(layer.scale(), layer.scale());
            g.translate(-layer.width() / 2, -layer.height() / 2);
            g.setComposite(AlphaComposite.getInstance(
                    AlphaComposite.SRC_OVER, (float) clamp01(layer.opacity())));
            // 이미지를 width×height로 늘려 그린다 — drawImage(image, 0, 0, w, h)와 동일
            g.drawImage(image, 0, 0,
                    (int) Math.round(layer.width()), (int) Math.round(layer.height()), null);

            g.setComposite(oldComposite);
            g.setTransform(oldTransform);
        }
    }

    // ── 그리기 순서 4: 셀 누끼 비네트 — 레이어 위 (최상단 후처리) ──────────────────────

    private void drawCellCutouts(Graphics2D g, ComposeSpec spec) {
        List<FrameLayout.Slot> slots = spec.slots();
        List<Boolean> cutouts = spec.cellCutouts();
        for (int i = 0; i < slots.size(); i++) {
            if (i >= cutouts.size() || !cutouts.get(i)) {
                continue;
            }
            FrameLayout.Slot slot = slots.get(i);
            // 캔버스 roundRect(r)의 Java2D 대응은 호(arc) 지름 2r. r은 프론트처럼 변 절반까지만
            double cornerRadius = Math.min(CUTOUT_CORNER_RADIUS,
                    Math.min(slot.width(), slot.height()) / 2.0);
            Shape rounded = new RoundRectangle2D.Double(
                    slot.x(), slot.y(), slot.width(), slot.height(),
                    cornerRadius * 2, cornerRadius * 2);
            double radius = Math.min(slot.width(), slot.height()) * VIGNETTE_RADIUS_RATIO;
            Point2D center = new Point2D.Double(
                    slot.x() + slot.width() / 2.0, slot.y() + slot.height() / 2.0);

            // 중심 0.6r까지 투명, r에서 어두움. r 밖은 마지막 색 유지 — 캔버스 그라디언트와 같은 동작
            Paint oldPaint = g.getPaint();
            g.setPaint(new RadialGradientPaint(center, (float) radius,
                    new float[]{VIGNETTE_INNER_STOP, 1f},
                    new Color[]{VIGNETTE_CENTER, VIGNETTE_EDGE}));
            g.fill(rounded);
            g.setPaint(oldPaint);

            Stroke oldStroke = g.getStroke();
            g.setStroke(new BasicStroke(CUTOUT_RING_WIDTH));
            g.setColor(CUTOUT_RING);
            g.draw(rounded);
            g.setStroke(oldStroke);
        }
    }

    // ── 공통 도구 ──────────────────────

    // cover: 비율 유지 + 가운데 정렬 + 넘치는 부분은 사각형 밖으로 못 나가게 클리핑
    private void drawCover(Graphics2D g, BufferedImage image, Rectangle2D rect) {
        Shape oldClip = g.getClip();
        g.clip(rect);
        double scale = Math.max(rect.getWidth() / image.getWidth(),
                rect.getHeight() / image.getHeight());
        double drawWidth = image.getWidth() * scale;
        double drawHeight = image.getHeight() * scale;
        AffineTransform transform = new AffineTransform();
        transform.translate(rect.getX() + (rect.getWidth() - drawWidth) / 2,
                rect.getY() + (rect.getHeight() - drawHeight) / 2);
        transform.scale(scale, scale);
        g.drawImage(image, transform, null);
        g.setClip(oldClip);
    }

    // #RGB 축약형은 #RRGGBB로 확장, 그 외 형식은 프론트 fallback과 같은 기본색
    private static Color parseHexColor(String value) {
        String hex = value.strip().replaceFirst("^#", "");
        if (hex.length() == 3 && hex.matches("[0-9a-fA-F]{3}")) {
            StringBuilder expanded = new StringBuilder(6);
            for (char c : hex.toCharArray()) {
                expanded.append(c).append(c);
            }
            hex = expanded.toString();
        }
        if (!hex.matches("[0-9a-fA-F]{6}")) {
            return IMAGE_BACKGROUND_BASE;
        }
        return new Color(Integer.parseInt(hex, 16));
    }

    private static BufferedImage decode(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IllegalArgumentException("이미지 형식을 인식할 수 없다");
            }
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("이미지 바이트를 읽을 수 없다", e);
        }
    }

    private static byte[] requireAsset(Map<String, byte[]> assets, String source) {
        byte[] bytes = assets.get(source);
        if (bytes == null) {
            throw new IllegalArgumentException("스펙이 참조하는 이미지가 없다: " + source);
        }
        return bytes;
    }

    private static byte[] encodePng(BufferedImage canvas) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(canvas, "png", out)) {
                throw new IllegalStateException("PNG 인코더를 찾을 수 없다");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("PNG 인코딩 실패", e);
        }
        return out.toByteArray();
    }

    private static double clamp01(double value) {
        return Math.min(1.0, Math.max(0.0, value));
    }

    private static void applyQualityHints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }
}
