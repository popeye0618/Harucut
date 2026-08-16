package com.harucut.storage.strategy;

import com.harucut.storage.enums.UploadType;
import com.harucut.storage.util.S3Keys;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 프레임 업로드 전략
@Component
public class FrameUploadPathStrategy implements UploadPathStrategy {

    @Override
    public UploadType getUploadType() {
        return UploadType.FRAME;
    }

    @Override
    public String generateKey(String publicId, String extension) {
        return S3Keys.userRoot(publicId) + "frames/" + UUID.randomUUID() + "." + extension;
    }
}
