package com.harucut.storage.strategy;

import com.harucut.storage.enums.UploadType;
import com.harucut.storage.util.S3Keys;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 네컷 합성 원본 사진 업로드 전략.
// 결과물(fourcuts/)과 폴더를 분리한다 — 원본은 임시라서, 나중에 S3 Lifecycle로
// "sources/만 N일 후 자동 삭제"를 걸어 고아(올려놓고 합성 안 한 파일)를 치우기 위함
@Component
public class FourcutSourceUploadPathStrategy implements UploadPathStrategy {

    @Override
    public UploadType getUploadType() {
        return UploadType.FOURCUT_SOURCE;
    }

    @Override
    public String generateKey(String publicId, String extension) {
        return S3Keys.userRoot(publicId) + "fourcuts/sources/" + UUID.randomUUID() + "." + extension;
    }
}
