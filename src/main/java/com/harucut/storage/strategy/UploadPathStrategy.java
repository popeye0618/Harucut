package com.harucut.storage.strategy;

import com.harucut.storage.enums.UploadType;

public interface UploadPathStrategy {

    UploadType getUploadType();

    String generateKey(String publicId, String extension);
}
