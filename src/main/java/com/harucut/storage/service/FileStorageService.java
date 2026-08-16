package com.harucut.storage.service;

import com.harucut.storage.dto.PresignedUploadResponse;
import com.harucut.storage.enums.ContentType;
import com.harucut.storage.enums.UploadType;

public interface FileStorageService {

    PresignedUploadResponse generatePresignedUploadUrl(UploadType type, String filename,
                                                       ContentType contentType, long fileSize,
                                                       String publicId);

    String generatePresignedGetUrl(String key);

    String generatePresignedDownloadUrl(String key, String downloadFileName);

    void delete(String key);
}
