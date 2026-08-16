package com.harucut.storage.util;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;

import java.net.URI;

public final class S3Keys {

    private S3Keys() {}

    public static String userRoot(String publicId) {
        return "uploads/users/" + publicId + "/";
    }

    // 입력(s3://, http(s):// URL, key)을 순수 S3 object key로 정규화. key를 추출할 수 없으면 GEN-002
    public static String normalizeToKey(String pathOrKey) {
        if (pathOrKey == null || pathOrKey.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "S3 path or key must not be blank.");
        }
        String value = pathOrKey.strip();
        if (value.startsWith("s3://") || value.startsWith("http://") || value.startsWith("https://")) {
            String path;
            try {
                path = URI.create(value).getPath();
            } catch (IllegalArgumentException e) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "Cannot extract S3 key from the given path.");
            }
            if (path == null || path.isBlank() || path.equals("/")) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "Cannot extract S3 key from the given path.");
            }
            return path.startsWith("/") ? path.substring(1) : path;
        }
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
