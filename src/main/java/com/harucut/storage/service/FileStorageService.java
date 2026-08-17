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

    // 서버가 직접 읽고 쓰는 경로 — 네컷 합성이 원본을 내려받고 결과를 올릴 때 쓴다.
    // presigned와 달리 사용자를 거치지 않으므로 소유권 검증은 호출자 책임이다
    byte[] downloadBytes(String key);

    void uploadBytes(String key, byte[] bytes, String contentType);
}
