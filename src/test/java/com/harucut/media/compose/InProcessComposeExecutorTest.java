package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout.Slot;
import com.harucut.storage.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("InProcessComposeExecutor")
class InProcessComposeExecutorTest {

    private static final List<String> SOURCE_KEYS = List.of(
            "uploads/u/s1.jpg", "uploads/u/s2.jpg", "uploads/u/s3.jpg", "uploads/u/s4.jpg");
    private static final String RESULT_KEY = "uploads/u/fourcuts/job-5.png";
    private static final byte[] PNG = {1, 2, 3};

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private FourcutRenderer fourcutRenderer;

    private InProcessComposeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new InProcessComposeExecutor(fileStorageService, fourcutRenderer);
    }

    @Test
    @DisplayName("원본 4장을 순서대로 내려받아 렌더러에 같은 순서로 전달한다")
    void sourcesDownloadedInOrder() {
        ComposeSpec spec = spec(new BackgroundAttributes.Color("#FFF"), List.of());
        SOURCE_KEYS.forEach(key ->
                given(fileStorageService.downloadBytes(key)).willReturn(key.getBytes()));
        given(fourcutRenderer.render(eq(spec), anyList(), anyMap())).willReturn(PNG);

        executor.execute(spec, SOURCE_KEYS, RESULT_KEY);

        ArgumentCaptor<List<byte[]>> captor = ArgumentCaptor.captor();
        then(fourcutRenderer).should().render(eq(spec), captor.capture(), anyMap());
        assertThat(captor.getValue()).extracting(String::new)
                .containsExactlyElementsOf(SOURCE_KEYS);
    }

    @Test
    @DisplayName("IMAGE 배경과 레이어 자산을 내려받아 assets로 전달하고, 중복 key는 한 번만 받는다")
    void assetsCollectedWithoutDuplicates() {
        ComposeSpec spec = spec(new BackgroundAttributes.Image("uploads/u/bg.png", 0.8, null),
                List.of(layer("uploads/u/sticker.png", 1),
                        layer("uploads/u/sticker.png", 2),
                        layer("uploads/u/text.png", 3)));
        given(fileStorageService.downloadBytes(any())).willAnswer(inv ->
                ((String) inv.getArgument(0)).getBytes());
        given(fourcutRenderer.render(eq(spec), anyList(), anyMap())).willReturn(PNG);

        executor.execute(spec, SOURCE_KEYS, RESULT_KEY);

        ArgumentCaptor<Map<String, byte[]>> captor = ArgumentCaptor.captor();
        then(fourcutRenderer).should().render(eq(spec), anyList(), captor.capture());
        assertThat(captor.getValue().keySet()).containsExactlyInAnyOrder(
                "uploads/u/bg.png", "uploads/u/sticker.png", "uploads/u/text.png");
        then(fileStorageService).should(times(1)).downloadBytes("uploads/u/sticker.png");
    }

    @Test
    @DisplayName("COLOR 배경이면 배경 다운로드가 없다")
    void colorBackgroundNotDownloaded() {
        ComposeSpec spec = spec(new BackgroundAttributes.Color("#FFF"), List.of());
        SOURCE_KEYS.forEach(key ->
                given(fileStorageService.downloadBytes(key)).willReturn(PNG));
        given(fourcutRenderer.render(eq(spec), anyList(), anyMap())).willReturn(PNG);

        executor.execute(spec, SOURCE_KEYS, RESULT_KEY);

        then(fileStorageService).should(times(4)).downloadBytes(any());
    }

    @Test
    @DisplayName("렌더 결과를 resultKey에 image/png로 올린다")
    void resultUploadedAsPng() {
        ComposeSpec spec = spec(new BackgroundAttributes.Color("#FFF"), List.of());
        SOURCE_KEYS.forEach(key ->
                given(fileStorageService.downloadBytes(key)).willReturn(PNG));
        given(fourcutRenderer.render(eq(spec), anyList(), anyMap())).willReturn(PNG);

        executor.execute(spec, SOURCE_KEYS, RESULT_KEY);

        then(fileStorageService).should().uploadBytes(RESULT_KEY, PNG, "image/png");
    }

    // ── fixtures ──────────────────────────────

    private static ComposeSpec spec(BackgroundAttributes background, List<ComposeSpec.Layer> layers) {
        return new ComposeSpec(100, 100, background,
                List.of(new Slot(0, 0, 10, 10), new Slot(10, 0, 10, 10),
                        new Slot(0, 10, 10, 10), new Slot(10, 10, 10, 10)),
                List.of(false, false, false, false), layers);
    }

    private static ComposeSpec.Layer layer(String source, int zIndex) {
        return new ComposeSpec.Layer(source, 0, 0, 10, 10, 1.0, 0, zIndex, 1.0);
    }
}
