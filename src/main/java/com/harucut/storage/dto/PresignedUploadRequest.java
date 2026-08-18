package com.harucut.storage.dto;

import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "업로드용 presigned URL 발급 요청")
public record PresignedUploadRequest(

        @NotNull(message = "업로드 타입은 필수입니다.")
        @Schema(description = """
                무엇을 올리는지. 이 값에 따라 S3 경로가 갈린다.

                `PROFILE` 프로필 이미지 ·
                `FRAME` 프레임 프리뷰 ·
                `FRAME_COMPONENT` 스티커·배경·사진 컴포넌트 ·
                `FOURCUT_SOURCE` 네컷 합성에 넣을 **원본** 사진

                ⚠️ **완성된 네컷을 올리는 타입은 없다.** 결과물은 합성 API 가 서버에서 만들어 저장한다.""",
                example = "PROFILE", requiredMode = Schema.RequiredMode.REQUIRED)
        UploadType type,

        @NotBlank(message = "파일명은 필수입니다.")
        @Schema(description = """
                확장자를 포함한 원본 파일명. **확장자만 쓰고 이름은 버린다** —
                저장되는 key 는 UUID 라 파일명이 노출되지 않는다.""",
                example = "profile_image.png", requiredMode = Schema.RequiredMode.REQUIRED)
        String filename,

        @NotNull(message = "콘텐츠 타입은 필수입니다.")
        @Schema(description = """
                MIME 이 아니라 **대문자 enum 이름**을 보낸다 (`image/png` 아니고 `PNG`).
                `filename` 의 확장자와 짝이 맞아야 한다 — 안 맞으면 415 다.
                JPEG 은 `jpg`·`jpeg` 둘 다 받는다.""",
                example = "PNG", requiredMode = Schema.RequiredMode.REQUIRED)
        ContentType contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = MAX_FILE_SIZE, message = "파일 크기는 10MB 이하여야 합니다.")
        @Schema(description = """
                바이트 단위 파일 크기. 1 ~ 10485760(10MB).

                ⚠️ **이 값이 서명에 Content-Length 로 들어간다.** URL 을 받은 뒤 다른 파일을 올리면
                크기가 달라져 S3 가 `SignatureDoesNotMatch` 로 거부한다. 발급과 업로드 사이에
                파일 객체를 그대로 들고 있을 것.""",
                example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        Long fileSize
) {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
}
