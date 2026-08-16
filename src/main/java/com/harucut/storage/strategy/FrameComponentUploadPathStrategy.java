package com.harucut.storage.strategy;

import com.harucut.storage.enums.UploadType;
import com.harucut.storage.util.S3Keys;
import org.springframework.stereotype.Component;

import java.util.UUID;

// 프레임 내부 컴포넌트 업로드 전략
@Component
public class FrameComponentUploadPathStrategy implements UploadPathStrategy {

    @Override
    public UploadType getUploadType() {
        return UploadType.FRAME_COMPONENT;
    }

    @Override
    public String generateKey(String publicId, String extension) {
        return S3Keys.userRoot(publicId) + "components/" + UUID.randomUUID() + "." + extension;
    }
}
