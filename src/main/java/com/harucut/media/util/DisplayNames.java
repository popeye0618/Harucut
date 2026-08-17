package com.harucut.media.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

// 표시 파일명 정제. 저장 전에 부른다 — 더러운 이름(경로·따옴표·개행·제어문자)을
// DB에 넣지 않는 것이 목적. 헤더 경계의 방어(ContentDispositions)와는 별개의 겹이다
public final class DisplayNames {

    private static final String FALLBACK_PREFIX = "harucut_";
    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final int MAX_LENGTH = 255;
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern VALID_EXTENSION = Pattern.compile("\\.[a-z0-9]{1,10}");

    private DisplayNames() {
    }

    // baseTime: 이름이 비었을 때 쓸 기준 시각. 호출자가 Clock을 거쳐 만들어 넘긴다
    public static String resolve(String preferredName, String s3Key, LocalDateTime baseTime) {
        Objects.requireNonNull(baseTime, "baseTime");
        String extension = extensionWithDot(s3Key);
        String base = sanitizeBaseName(preferredName);
        if (base.isBlank()) {
            base = FALLBACK_PREFIX + FALLBACK_TIME.format(baseTime);
        }
        int baseLimit = MAX_LENGTH - extension.length();
        if (base.length() > baseLimit) {
            base = base.substring(0, baseLimit);
        }
        return base + extension;
    }

    private static String sanitizeBaseName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        String value = name.strip()
                .replace("\\", "/")
                .replace("\"", "")
                .replace("\r", "")
                .replace("\n", "")
                .replaceAll("/+$", "");   // 꼬리 슬래시 제거 — "사진/" 같은 입력
        int slashIndex = value.lastIndexOf('/');
        if (slashIndex >= 0) {
            value = value.substring(slashIndex + 1);   // 경로면 마지막 조각만 남긴다
        }
        value = removeExtension(value);
        value = CONTROL_CHARS.matcher(value).replaceAll("");
        value = WHITESPACE.matcher(value).replaceAll(" ");
        return value.strip();
    }

    // 확장자는 s3Key에서만 가져온다 — 소문자, 영숫자 1~10자만 인정
    private static String extensionWithDot(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return "";
        }
        int dotIndex = s3Key.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == s3Key.length() - 1) {
            return "";
        }
        String extension = s3Key.substring(dotIndex).toLowerCase();
        return VALID_EXTENSION.matcher(extension).matches() ? extension : "";
    }

    // 맨 앞의 점(.hidden)은 확장자가 아니라 이름의 일부로 본다
    private static String removeExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex <= 0 ? filename : filename.substring(0, dotIndex);
    }
}
