package com.harucut.frame.service;

import com.harucut.frame.enums.ComponentType;
import com.harucut.storage.service.FileStorageService;
import com.harucut.storage.service.S3Deleter;
import com.harucut.storage.util.S3Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;

// 프레임이 참조하는 S3 자산의 경계 담당: 저장 전 key 정규화, 응답 시 presign, 커밋 후 삭제 예약.
// 관리 대상(uploads/ 아래)만 손댄다 — PHOTO 외 컴포넌트(STICKER=정적 경로, TEXT=본문 텍스트)와
// 외부 URL은 그대로 통과시킨다.
@Component
@RequiredArgsConstructor
public class FrameAssetManager {

    private final FileStorageService fileStorageService;
    private final S3Deleter s3Deleter;

    // 저장 직전: PHOTO의 source만 순수 key로. presigned URL이 들어와도 key만 남긴다
    public String normalizeSource(ComponentType type, String source) {
        if (type != ComponentType.PHOTO) {
            return source;
        }
        return S3Keys.normalizeManagedKey(source);
    }

    // 배경 IMAGE key와 previewKey용 — 항상 이미지 의미라 타입 인자가 필요 없다
    public String normalizeImageKey(String pathOrKey) {
        return S3Keys.normalizeManagedKey(pathOrKey);
    }

    // 응답 조립: PHOTO이고 관리 대상이면 presigned GET URL, 아니면 원본 그대로
    public String resolveSource(ComponentType type, String source) {
        if (type != ComponentType.PHOTO) {
            return source;
        }
        return presignIfManaged(source);
    }

    public String resolveImageSource(String pathOrKey) {
        return presignIfManaged(pathOrKey);
    }

    // 커밋 후 삭제 예약 — 거르기와 발행은 storage 공용 부품이 한다 (media와 공유)
    public void deleteAfterCommit(Collection<String> keys) {
        s3Deleter.deleteAfterCommit(keys);
    }

    private String presignIfManaged(String source) {
        String normalized = S3Keys.normalizeManagedKey(source);
        return S3Keys.isManagedKey(normalized)
                ? fileStorageService.generatePresignedGetUrl(normalized)
                : source;
    }
}
