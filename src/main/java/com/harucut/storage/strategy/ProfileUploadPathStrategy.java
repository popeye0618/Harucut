package com.harucut.storage.strategy;

import com.harucut.storage.enums.UploadType;
import com.harucut.storage.util.S3Keys;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 프로필 사진 업로드 전략
@Component
public class ProfileUploadPathStrategy implements UploadPathStrategy {

    @Override
    public UploadType getUploadType() {
        return UploadType.PROFILE;
    }

    @Override
    public String generateKey(String publicId, String extension) {
        return S3Keys.userRoot(publicId) + "profile/" + UUID.randomUUID() + "." + extension;
    }
}
