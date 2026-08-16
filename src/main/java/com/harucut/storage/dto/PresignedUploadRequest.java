package com.harucut.storage.dto;

import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignedUploadRequest(
        @NotNull(message = "업로드 타입은 필수입니다.")
        UploadType type,

        @NotBlank(message = "파일명은 필수입니다.")
        String filename,

        @NotNull(message = "콘텐츠 타입은 필수입니다.")
        ContentType contentType,

        @NotNull(message = "파일 크기는 필수입니다.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        @Max(value = MAX_FILE_SIZE, message = "파일 크기는 10MB 이하여야 합니다.")
        Long fileSize
) {
    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
}
