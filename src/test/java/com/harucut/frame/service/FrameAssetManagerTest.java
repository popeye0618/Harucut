package com.harucut.frame.service;

import com.harucut.frame.enums.ComponentType;
import com.harucut.storage.event.S3DeleteEvent;
import com.harucut.storage.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("FrameAssetManager")
class FrameAssetManagerTest {

    private static final String KEY = "uploads/users/AbCdEf12Gh/components/photo1.png";
    private static final String MANAGED_URL =
            "https://harucut-test.s3.ap-northeast-2.amazonaws.com/" + KEY + "?X-Amz-Signature=abc";
    private static final String EXTERNAL_URL = "https://cdn.example.com/stickers/heart.png";
    private static final String PRESIGNED = "https://harucut-test.s3.amazonaws.com/presigned";

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private FrameAssetManager assetManager;

    @BeforeEach
    void setUp() {
        assetManager = new FrameAssetManager(fileStorageService, eventPublisher);
    }

    @Nested
    @DisplayName("normalizeSource — 저장 방향")
    class Normalize {

        @Test
        @DisplayName("PHOTO의 presigned URL은 순수 key로 정규화된다")
        void photoUrlBecomesKey() {
            assertThat(assetManager.normalizeSource(ComponentType.PHOTO, MANAGED_URL)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("PHOTO여도 외부 URL은 건드리지 않는다")
        void photoExternalUrlUntouched() {
            assertThat(assetManager.normalizeSource(ComponentType.PHOTO, EXTERNAL_URL))
                    .isEqualTo(EXTERNAL_URL);
        }

        @Test
        @DisplayName("STICKER는 정적 자산 경로라 앞 슬래시까지 그대로 보존된다")
        void stickerUntouched() {
            // PHOTO였다면 앞 슬래시가 떼졌을 입력 — 타입 분기가 실제로 보호하는 것을 고정
            assertThat(assetManager.normalizeSource(ComponentType.STICKER, "/static/stickers/heart.png"))
                    .isEqualTo("/static/stickers/heart.png");
        }

        @Test
        @DisplayName("TEXT의 source는 본문 텍스트라 절대 손대지 않는다")
        void textUntouched() {
            // URI로 파싱하면 죽는 입력 — 파싱 자체를 안 한다는 증명
            assertThat(assetManager.normalizeSource(ComponentType.TEXT, "봄 여행 🌸 4컷"))
                    .isEqualTo("봄 여행 🌸 4컷");
        }

        @Test
        @DisplayName("배경·프리뷰용 normalizeImageKey도 URL을 key로 만든다")
        void imageKeyNormalized() {
            assertThat(assetManager.normalizeImageKey(MANAGED_URL)).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("resolveSource — 응답 방향")
    class Resolve {

        @Test
        @DisplayName("PHOTO의 관리 key는 presigned GET URL로 바뀐다")
        void photoManagedKeyPresigned() {
            given(fileStorageService.generatePresignedGetUrl(KEY)).willReturn(PRESIGNED);

            assertThat(assetManager.resolveSource(ComponentType.PHOTO, KEY)).isEqualTo(PRESIGNED);
        }

        @Test
        @DisplayName("PHOTO여도 외부 URL은 서명 없이 그대로 나간다")
        void photoExternalNotPresigned() {
            assertThat(assetManager.resolveSource(ComponentType.PHOTO, EXTERNAL_URL))
                    .isEqualTo(EXTERNAL_URL);
            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("TEXT는 스토리지에 아예 묻지 않는다")
        void textNotPresigned() {
            assertThat(assetManager.resolveSource(ComponentType.TEXT, "봄 여행 🌸"))
                    .isEqualTo("봄 여행 🌸");
            then(fileStorageService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("URL이 저장된 레거시 행도 재정규화 후 서명된다 — 읽기의 방어선")
        void legacyStoredUrlStillPresigned() {
            given(fileStorageService.generatePresignedGetUrl(KEY)).willReturn(PRESIGNED);

            assertThat(assetManager.resolveImageSource(MANAGED_URL)).isEqualTo(PRESIGNED);
        }
    }

    @Nested
    @DisplayName("deleteAfterCommit — 삭제 예약")
    class DeleteAfterCommit {

        @Test
        @DisplayName("null·빈 값·외부 URL을 거르고 중복을 제거해 이벤트로 발행한다")
        void filtersAndDeduplicates() {
            assetManager.deleteAfterCommit(Arrays.asList(KEY, null, "  ", EXTERNAL_URL, KEY));

            ArgumentCaptor<S3DeleteEvent> captor = ArgumentCaptor.forClass(S3DeleteEvent.class);
            then(eventPublisher).should().publishEvent(captor.capture());
            assertThat(captor.getValue().keys()).containsExactly(KEY);
        }

        @Test
        @DisplayName("지울 관리 key가 하나도 없으면 이벤트를 발행하지 않는다")
        void nothingManagedNoPublish() {
            assetManager.deleteAfterCommit(Arrays.asList(null, "  ", EXTERNAL_URL));

            then(eventPublisher).shouldHaveNoInteractions();
        }
    }
}
