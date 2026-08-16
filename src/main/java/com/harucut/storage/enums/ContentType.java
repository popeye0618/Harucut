package com.harucut.storage.enums;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

@Getter
@RequiredArgsConstructor
public enum ContentType {

    JPEG("image/jpeg", Set.of("jpg", "jpeg")),
    PNG("image/png", Set.of("png")),
    WEBP("image/webp", Set.of("webp")),
    GIF("image/gif", Set.of("gif"));

    private final String mimeType;
    private final Set<String> extensions;

    // 통과하면 "정규화된 확장자"를 돌려준다. S3 key 생성에 이 값을 쓴다
    public String validateExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new BusinessException(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE, "확장자가 비어 있습니다.");
        }

        String normalized = extension.strip().toLowerCase();

        if (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }

        if (!extensions.contains(normalized)) {
            throw new BusinessException(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "지원하지 않는 MIME/확장자: " + mimeType + " / " + normalized);
        }

        return normalized;
    }
}
