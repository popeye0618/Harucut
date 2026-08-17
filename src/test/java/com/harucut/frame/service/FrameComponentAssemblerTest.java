package com.harucut.frame.service;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.dto.FrameCreateRequest;
import com.harucut.frame.dto.FrameResponse;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.entity.FrameComponent;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;
import com.harucut.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("FrameComponentAssembler")
class FrameComponentAssemblerTest {

    private static final String PHOTO_URL =
            "https://harucut-test.s3.amazonaws.com/uploads/users/AbCdEf12Gh/components/photo1.png";
    private static final String PHOTO_KEY = "uploads/users/AbCdEf12Gh/components/photo1.png";
    private static final String PREVIEW_KEY = "uploads/users/AbCdEf12Gh/webm/preview.png";
    private static final String BG_KEY = "uploads/users/AbCdEf12Gh/components/bg.png";

    @Mock
    private FrameAssetManager frameAssetManager;

    private FrameComponentAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new FrameComponentAssembler(frameAssetManager);
    }

    @Nested
    @DisplayName("createComponents — 요청 → 엔티티")
    class CreateComponents {

        @Test
        @DisplayName("컴포넌트 목록이 null이면 빈 리스트다")
        void nullBecomesEmptyList() {
            assertThat(assembler.createComponents(null)).isEmpty();
            then(frameAssetManager).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("PHOTO source는 정규화된 key로 저장되고 나머지 필드는 그대로 옮겨진다")
        void mapsFieldsAndNormalizesSource() {
            given(frameAssetManager.normalizeSource(ComponentType.PHOTO, PHOTO_URL)).willReturn(PHOTO_KEY);
            FrameCreateRequest.ComponentRequest request = new FrameCreateRequest.ComponentRequest(
                    "comp-1", ComponentType.PHOTO, PHOTO_URL,
                    120.5, 220.0, 360.0, 480.0, 1.5, 45.0, 3, Map.of("borderRadius", 8));

            List<FrameComponent> components = assembler.createComponents(List.of(request));

            FrameComponent component = components.get(0);
            assertThat(component.getSource()).isEqualTo(PHOTO_KEY);
            assertThat(component.getType()).isEqualTo(ComponentType.PHOTO);
            assertThat(component.getX()).isEqualTo(120.5);
            assertThat(component.getY()).isEqualTo(220.0);
            assertThat(component.getWidth()).isEqualTo(360.0);
            assertThat(component.getHeight()).isEqualTo(480.0);
            assertThat(component.getScale()).isEqualTo(1.5);
            assertThat(component.getRotation()).isEqualTo(45.0);
            assertThat(component.getZIndex()).isEqualTo(3);
            assertThat(component.getStyle()).isEqualTo(Map.of("borderRadius", 8));
        }

        @Test
        @DisplayName("styleJson이 null이어도 엔티티의 style은 빈 맵이다")
        void nullStyleBecomesEmptyMap() {
            given(frameAssetManager.normalizeSource(ComponentType.TEXT, "봄 여행")).willReturn("봄 여행");
            FrameCreateRequest.ComponentRequest request = new FrameCreateRequest.ComponentRequest(
                    null, ComponentType.TEXT, "봄 여행",
                    0.0, 0.0, null, null, null, 0.0, 0, null);

            List<FrameComponent> components = assembler.createComponents(List.of(request));

            assertThat(components.get(0).getStyle()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("extractPhotoKeys")
    class ExtractPhotoKeys {

        @Test
        @DisplayName("PHOTO의 source만 수집한다 — STICKER·TEXT는 우리 버킷 소유가 아니다")
        void collectsOnlyPhotoSources() {
            List<FrameComponent> components = List.of(
                    component(ComponentType.PHOTO, PHOTO_KEY),
                    component(ComponentType.STICKER, "/static/stickers/heart.png"),
                    component(ComponentType.TEXT, "봄 여행"));

            assertThat(assembler.extractPhotoKeys(components)).containsExactly(PHOTO_KEY);
        }
    }

    @Nested
    @DisplayName("배경 정규화·key 추출")
    class Background {

        @Test
        @DisplayName("IMAGE 배경의 key가 정규화되고 url 흔적은 비워진다")
        void normalizesImageKeyAndStripsUrl() {
            given(frameAssetManager.normalizeImageKey(PHOTO_URL)).willReturn(BG_KEY);
            BackgroundAttributes.Image withUrl =
                    new BackgroundAttributes.Image(PHOTO_URL, 0.8, null).withUrl("https://presigned");

            BackgroundAttributes normalized = assembler.normalizeBackground(withUrl);

            assertThat(normalized).isEqualTo(new BackgroundAttributes.Image(BG_KEY, 0.8, null));
        }

        @Test
        @DisplayName("COLOR 배경은 손대지 않고 그대로 통과한다")
        void colorPassesThrough() {
            BackgroundAttributes color = new BackgroundAttributes.Color("#FFE4E1");

            assertThat(assembler.normalizeBackground(color)).isSameAs(color);
            then(frameAssetManager).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("삭제 후보 key는 IMAGE에서만 나오고 COLOR는 null이다")
        void extractsKeyOnlyFromImage() {
            assertThat(assembler.extractBackgroundKey(new BackgroundAttributes.Image(BG_KEY, 0.8, null)))
                    .isEqualTo(BG_KEY);
            assertThat(assembler.extractBackgroundKey(new BackgroundAttributes.Color("#FFF"))).isNull();
        }
    }

    @Nested
    @DisplayName("toFrameResponse — 엔티티 → 응답")
    class ToFrameResponse {

        @Test
        @DisplayName("컴포넌트가 zIndex 오름차순으로 나오고 동률은 추가 순서를 지킨다")
        void sortsByZIndexStably() {
            given(frameAssetManager.resolveImageSource(PREVIEW_KEY)).willReturn("https://preview");
            given(frameAssetManager.resolveSource(any(), anyString()))
                    .willAnswer(invocation -> invocation.getArgument(1));
            Frame frame = colorFrame();
            frame.addComponent(componentWithZIndex("셋", 3));
            frame.addComponent(componentWithZIndex("일-먼저", 1));
            frame.addComponent(componentWithZIndex("일-나중", 1));
            frame.addComponent(componentWithZIndex("둘", 2));

            FrameResponse response = assembler.toFrameResponse(frame);

            assertThat(response.components())
                    .extracting(FrameResponse.ComponentResponse::source)
                    .containsExactly("일-먼저", "일-나중", "둘", "셋");
        }

        @Test
        @DisplayName("width/height null은 0.0으로, scale null은 1.0으로 보정된다")
        void nullDimensionDefaults() {
            given(frameAssetManager.resolveImageSource(PREVIEW_KEY)).willReturn("https://preview");
            given(frameAssetManager.resolveSource(any(), anyString()))
                    .willAnswer(invocation -> invocation.getArgument(1));
            Frame frame = colorFrame();
            frame.addComponent(FrameComponent.builder()
                    .source("봄 여행").type(ComponentType.TEXT).build());

            FrameResponse.ComponentResponse component =
                    assembler.toFrameResponse(frame).components().get(0);

            assertThat(component.width()).isZero();
            assertThat(component.height()).isZero();
            assertThat(component.scale()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("캔버스 크기는 frameType 파생 고정값이고 key·source가 분리돼 나간다")
        void canvasDerivedAndSourceResolved() {
            given(frameAssetManager.resolveImageSource(PREVIEW_KEY)).willReturn("https://preview");
            given(frameAssetManager.resolveSource(ComponentType.PHOTO, PHOTO_KEY))
                    .willReturn("https://photo-presigned");
            Frame frame = colorFrame();
            frame.addComponent(component(ComponentType.PHOTO, PHOTO_KEY));

            FrameResponse response = assembler.toFrameResponse(frame);

            assertThat(response.canvasWidth()).isEqualTo(2000);
            assertThat(response.canvasHeight()).isEqualTo(6000);
            assertThat(response.source()).isEqualTo("https://preview");
            assertThat(response.isSystem()).isTrue();
            FrameResponse.ComponentResponse component = response.components().get(0);
            assertThat(component.source()).isEqualTo("https://photo-presigned");
            assertThat(component.key()).isEqualTo(PHOTO_KEY);
        }

        @Test
        @DisplayName("IMAGE 배경에는 presigned url이 주입돼 나간다")
        void imageBackgroundGetsUrl() {
            given(frameAssetManager.resolveImageSource(PREVIEW_KEY)).willReturn("https://preview");
            given(frameAssetManager.resolveImageSource(BG_KEY)).willReturn("https://bg-presigned");
            Frame frame = Frame.system("기본", "설명", PREVIEW_KEY, FrameType.CLASSIC,
                    new BackgroundAttributes.Image(BG_KEY, 0.8, null));

            FrameResponse response = assembler.toFrameResponse(frame);

            assertThat(response.background())
                    .isEqualTo(new BackgroundAttributes.Image(BG_KEY, 0.8, "https://bg-presigned"));
        }
    }

    @Nested
    @DisplayName("assembleOwned / assembleSystem — 생성 조립")
    class Assemble {

        @Test
        @DisplayName("소유 프레임은 소유자를 갖고 프리뷰·배경이 정규화되어 조립된다")
        void assemblesOwnedFrame() {
            User user = User.localUser("user@harucut.com", "encoded", "하루컷");
            given(frameAssetManager.normalizeImageKey("preview-url")).willReturn(PREVIEW_KEY);
            FrameCreateRequest request = new FrameCreateRequest("제목", null, "preview-url",
                    FrameType.CLASSIC, null, null, new BackgroundAttributes.Color("#FFF"), null);

            Frame frame = assembler.assembleOwned(user, request);

            assertThat(frame.getUser()).isSameAs(user);
            assertThat(frame.isSystem()).isFalse();
            assertThat(frame.getPreviewKey()).isEqualTo(PREVIEW_KEY);
            assertThat(frame.getDescription()).isEmpty();
            assertThat(frame.getBackground()).isEqualTo(new BackgroundAttributes.Color("#FFF"));
        }

        @Test
        @DisplayName("시스템 프레임은 소유자 없이 조립되고 컴포넌트 source도 정규화된다")
        void assemblesSystemFrame() {
            given(frameAssetManager.normalizeImageKey("preview-url")).willReturn(PREVIEW_KEY);
            given(frameAssetManager.normalizeSource(ComponentType.PHOTO, PHOTO_URL)).willReturn(PHOTO_KEY);
            FrameCreateRequest request = new FrameCreateRequest("기본", "설명", "preview-url",
                    FrameType.CLASSIC, null, null, new BackgroundAttributes.Color("#FFF"),
                    List.of(componentRequest(ComponentType.PHOTO, PHOTO_URL)));

            Frame frame = assembler.assembleSystem(request);

            assertThat(frame.getUser()).isNull();
            assertThat(frame.isSystem()).isTrue();
            assertThat(frame.getComponents()).extracting(FrameComponent::getSource)
                    .containsExactly(PHOTO_KEY);
        }
    }

    @Nested
    @DisplayName("replaceContent — 전체 교체와 잃은 참조 수집")
    class ReplaceContent {

        @Test
        @DisplayName("교체된 배경·프리뷰·빠진 사진만 삭제 예약된다 — kept는 절대 지우지 않는다")
        void collectsOnlyOrphanedKeys() {
            Frame frame = Frame.system("옛 제목", "설명", "uploads/old-preview.png", FrameType.CLASSIC,
                    new BackgroundAttributes.Image("uploads/old-bg.png", 0.8, null));
            frame.addComponent(component(ComponentType.PHOTO, "uploads/old1.png"));
            frame.addComponent(component(ComponentType.PHOTO, "uploads/kept.png"));
            FrameCreateRequest request = new FrameCreateRequest("새 제목", null, "new-preview-url",
                    FrameType.CLASSIC, null, null,
                    new BackgroundAttributes.Image("new-bg-url", 0.5, null),
                    List.of(componentRequest(ComponentType.PHOTO, "uploads/kept.png"),
                            componentRequest(ComponentType.PHOTO, "uploads/new1.png")));
            given(frameAssetManager.normalizeImageKey("new-bg-url")).willReturn("uploads/new-bg.png");
            given(frameAssetManager.normalizeImageKey("new-preview-url")).willReturn("uploads/new-preview.png");
            given(frameAssetManager.normalizeSource(ComponentType.PHOTO, "uploads/kept.png"))
                    .willReturn("uploads/kept.png");
            given(frameAssetManager.normalizeSource(ComponentType.PHOTO, "uploads/new1.png"))
                    .willReturn("uploads/new1.png");

            assembler.replaceContent(frame, request);

            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.captor();
            then(frameAssetManager).should().deleteAfterCommit(captor.capture());
            assertThat(captor.getValue()).containsExactly(
                    "uploads/old-bg.png", "uploads/old-preview.png", "uploads/old1.png");
            assertThat(frame.getTitle()).isEqualTo("새 제목");
            assertThat(frame.getPreviewKey()).isEqualTo("uploads/new-preview.png");
            assertThat(frame.getBackground())
                    .isEqualTo(new BackgroundAttributes.Image("uploads/new-bg.png", 0.5, null));
            assertThat(frame.getComponents()).extracting(FrameComponent::getSource)
                    .containsExactly("uploads/kept.png", "uploads/new1.png");
        }

        @Test
        @DisplayName("배경·프리뷰·사진이 전부 그대로면 아무것도 삭제 예약되지 않는다")
        void keepsReusedKeys() {
            Frame frame = Frame.system("제목", "설명", "uploads/preview.png", FrameType.CLASSIC,
                    new BackgroundAttributes.Image("uploads/bg.png", 0.8, null));
            frame.addComponent(component(ComponentType.PHOTO, "uploads/kept.png"));
            FrameCreateRequest request = new FrameCreateRequest("제목", null, "uploads/preview.png",
                    FrameType.CLASSIC, null, null,
                    new BackgroundAttributes.Image("uploads/bg.png", 0.8, null),
                    List.of(componentRequest(ComponentType.PHOTO, "uploads/kept.png")));
            given(frameAssetManager.normalizeImageKey("uploads/bg.png")).willReturn("uploads/bg.png");
            given(frameAssetManager.normalizeImageKey("uploads/preview.png")).willReturn("uploads/preview.png");
            given(frameAssetManager.normalizeSource(ComponentType.PHOTO, "uploads/kept.png"))
                    .willReturn("uploads/kept.png");

            assembler.replaceContent(frame, request);

            ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.captor();
            then(frameAssetManager).should().deleteAfterCommit(captor.capture());
            assertThat(captor.getValue()).isEmpty();
        }
    }

    @Nested
    @DisplayName("collectAllKeys — 삭제용 key 수집")
    class CollectAllKeys {

        @Test
        @DisplayName("사진·배경·프리뷰 순으로 전부 수집한다")
        void collectsPhotosBackgroundPreview() {
            Frame frame = Frame.system("제목", "설명", PREVIEW_KEY, FrameType.CLASSIC,
                    new BackgroundAttributes.Image(BG_KEY, 0.8, null));
            frame.addComponent(component(ComponentType.PHOTO, PHOTO_KEY));

            assertThat(assembler.collectAllKeys(frame))
                    .containsExactly(PHOTO_KEY, BG_KEY, PREVIEW_KEY);
        }

        @Test
        @DisplayName("COLOR 배경은 null로 실려 간다 — deleteAfterCommit 필터가 거른다는 계약")
        void colorBackgroundYieldsNull() {
            Frame frame = colorFrame();
            frame.addComponent(component(ComponentType.PHOTO, PHOTO_KEY));

            assertThat(assembler.collectAllKeys(frame))
                    .containsExactly(PHOTO_KEY, null, PREVIEW_KEY);
        }
    }

    private static FrameCreateRequest.ComponentRequest componentRequest(ComponentType type, String source) {
        return new FrameCreateRequest.ComponentRequest(
                null, type, source, null, null, null, null, null, null, null, null);
    }

    private static Frame colorFrame() {
        return Frame.system("기본", "설명", PREVIEW_KEY, FrameType.CLASSIC,
                new BackgroundAttributes.Color("#FFE4E1"));
    }

    private static FrameComponent component(ComponentType type, String source) {
        return FrameComponent.builder().source(source).type(type).build();
    }

    private static FrameComponent componentWithZIndex(String source, int zIndex) {
        return FrameComponent.builder()
                .source(source).type(ComponentType.TEXT).zIndex(zIndex).build();
    }
}
