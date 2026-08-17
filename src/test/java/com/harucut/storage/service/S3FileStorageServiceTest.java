package com.harucut.storage.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.storage.config.AwsProperties;
import com.harucut.storage.dto.PresignedUploadResponse;
import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;
import com.harucut.storage.strategy.FourcutSourceUploadPathStrategy;
import com.harucut.storage.strategy.FrameComponentUploadPathStrategy;
import com.harucut.storage.strategy.FrameUploadPathStrategy;
import com.harucut.storage.strategy.ProfileUploadPathStrategy;
import com.harucut.storage.util.ContentDispositions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.then;

// 서명은 네트워크 호출이 아니라 로컬 계산이다 - 더미 자격증명으로 진짜 Presigner를 돌려
// 실제 URL 구조(서명 헤더, 만료, disposition)를 검증한다. 목으로는 이 검증이 불가능하다
@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileStorageService")
class S3FileStorageServiceTest {

    private static final String BUCKET = "harucut-test";
    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final String KEY = "uploads/users/" + PUBLIC_ID + "/profile/a1b2c3.png";

    @Mock
    private S3Client s3Client;

    private S3Presigner s3Presigner;
    private S3FileStorageService service;

    @BeforeEach
    void setUp() {
        s3Presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")))
                .build();
        service = new S3FileStorageService(s3Presigner, s3Client, properties(), List.of(
                new ProfileUploadPathStrategy(),
                new FrameUploadPathStrategy(),
                new FrameComponentUploadPathStrategy(),
                new FourcutSourceUploadPathStrategy()));
    }

    @AfterEach
    void tearDown() {
        s3Presigner.close();
    }

    @Nested
    @DisplayName("generatePresignedUploadUrl")
    class GeneratePresignedUploadUrl {

        @Test
        @DisplayName("PROFILE + profile.png + PNG → key가 uploads/users/{publicId}/profile/{uuid}.png다")
        void profileKeyShape() {
            PresignedUploadResponse response = upload(UploadType.PROFILE, "profile.png", ContentType.PNG);

            assertThat(response.key())
                    .matches("uploads/users/" + PUBLIC_ID + "/profile/[0-9a-f\\-]{36}\\.png");
        }

        @Test
        @DisplayName("업로드 타입별 경로 조각이 각각 맞다 — FRAME은 webm이 아니라 frames다")
        void typeSpecificPaths() {
            assertThat(upload(UploadType.FRAME, "preview.png", ContentType.PNG).key())
                    .contains("/frames/")
                    .doesNotContain("webm");
            assertThat(upload(UploadType.FRAME_COMPONENT, "sticker.png", ContentType.PNG).key())
                    .contains("/components/");
        }

        @Test
        @DisplayName("FOURCUT_SOURCE는 결과물과 분리된 fourcuts/sources/ 아래에 생긴다")
        void fourcutSourceKeyShape() {
            PresignedUploadResponse response =
                    upload(UploadType.FOURCUT_SOURCE, "shot1.jpg", ContentType.JPEG);

            assertThat(response.key())
                    .matches("uploads/users/" + PUBLIC_ID + "/fourcuts/sources/[0-9a-f\\-]{36}\\.jpg");
        }

        @Test
        @DisplayName("대문자 확장자(.PNG)는 key에서 소문자로 정규화된다")
        void upperCaseExtensionNormalized() {
            assertThat(upload(UploadType.PROFILE, "photo.PNG", ContentType.PNG).key())
                    .endsWith(".png");
        }

        @Test
        @DisplayName("MIME과 확장자가 어긋나면(PNG + .jpg) GEN-051을 던진다")
        void mimeExtensionMismatch() {
            assertThatThrownBy(() -> upload(UploadType.PROFILE, "photo.jpg", ContentType.PNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("확장자 없는 파일명은 GEN-051을 던진다")
        void filenameWithoutExtension() {
            assertThatThrownBy(() -> upload(UploadType.PROFILE, "photo", ContentType.PNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("지원하지 않는 확장자(.exe)는 GEN-051을 던진다")
        void unsupportedExtension() {
            assertThatThrownBy(() -> upload(UploadType.PROFILE, "virus.exe", ContentType.PNG))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("업로드 URL의 서명 대상에 content-length가 들어간다 — 크기를 바꿔 올리면 S3가 거부한다")
        void signsContentLength() {
            PresignedUploadResponse response = upload(UploadType.PROFILE, "profile.png", ContentType.PNG);

            assertThat(response.uploadUrl())
                    .contains("X-Amz-SignedHeaders=")
                    .contains("content-length")
                    .contains("X-Amz-Expires=86400");
        }

        @Test
        @DisplayName("응답에 실제 MIME과 PT24H 만료, 버킷·key가 담긴 URL이 들어간다")
        void responseMetadata() {
            PresignedUploadResponse response = upload(UploadType.PROFILE, "profile.png", ContentType.PNG);

            assertThat(response.contentType()).isEqualTo("image/png");
            assertThat(response.expiresIn()).isEqualTo(Duration.ofHours(24));
            assertThat(response.uploadUrl()).contains(BUCKET).contains(response.key());
        }

        private PresignedUploadResponse upload(UploadType type, String filename, ContentType contentType) {
            return service.generatePresignedUploadUrl(type, filename, contentType, 123456L, PUBLIC_ID);
        }
    }

    @Nested
    @DisplayName("generatePresignedGetUrl")
    class GeneratePresignedGetUrl {

        @Test
        @DisplayName("버킷과 key가 담긴 URL을 만들고 다운로드 disposition은 붙지 않는다")
        void plainGetUrl() {
            String url = service.generatePresignedGetUrl(KEY);

            assertThat(url)
                    .contains(BUCKET)
                    .contains(KEY)
                    .doesNotContain("response-content-disposition");
        }
    }

    @Nested
    @DisplayName("generatePresignedDownloadUrl")
    class GeneratePresignedDownloadUrl {

        @Test
        @DisplayName("한글 파일명이 filename*=UTF-8'' 형태로 disposition에 실린다")
        void koreanFilenameDisposition() {
            String url = service.generatePresignedDownloadUrl(KEY, "한글.png");

            assertThat(url).contains("response-content-type=application%2Foctet-stream");
            assertThat(dispositionOf(url)).isEqualTo(ContentDispositions.attachment("한글.png"));
        }

        @Test
        @DisplayName("파일명이 없으면 key의 마지막 조각을 파일명으로 쓴다")
        void fallsBackToFilenameFromKey() {
            String url = service.generatePresignedDownloadUrl(KEY, null);

            assertThat(dispositionOf(url)).isEqualTo(ContentDispositions.attachment("a1b2c3.png"));
        }

        private String dispositionOf(String url) {
            String raw = url.split("response-content-disposition=")[1].split("&")[0];
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("버킷과 key로 S3 삭제를 호출한다")
        void deletesByBucketAndKey() {
            service.delete(KEY);

            ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.captor();
            then(s3Client).should().deleteObject(captor.capture());
            assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
            assertThat(captor.getValue().key()).isEqualTo(KEY);
        }
    }

    @Nested
    @DisplayName("생성자")
    class Constructor {

        @Test
        @DisplayName("전략이 하나라도 빠지면 기동 시점에 IllegalStateException으로 죽는다")
        void missingStrategyFailsStartup() {
            assertThatThrownBy(() -> new S3FileStorageService(s3Presigner, s3Client, properties(),
                    List.of(new ProfileUploadPathStrategy())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FRAME");
        }

        @Test
        @DisplayName("같은 타입 전략이 둘이면 기동 시점에 죽는다")
        void duplicateStrategyFailsStartup() {
            assertThatThrownBy(() -> new S3FileStorageService(s3Presigner, s3Client, properties(),
                    List.of(new ProfileUploadPathStrategy(), new ProfileUploadPathStrategy(),
                            new FrameUploadPathStrategy(), new FrameComponentUploadPathStrategy())))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    private AwsProperties properties() {
        return new AwsProperties("ap-northeast-2", new AwsProperties.S3(BUCKET));
    }
}
