package com.harucut.storage.util;

import java.nio.charset.StandardCharsets;

public final class ContentDispositions {

    private ContentDispositions() {
    }

    public static String attachment(String filename) {
        String sanitized = sanitize(filename);
        return "attachment; filename=\"" + asciiFallback(sanitized)
                + "\"; filename*=UTF-8''" + encodeRfc5987(sanitized);
    }

    // 헤더 인젝션 차단: 따옴표·개행이 살아 있으면 Content-Disposition 값 밖으로 탈출할 수 있다
    private static String sanitize(String filename) {
        String sanitized = filename
                .replace("\\", "")
                .replace("/", "_")
                .replace("\"", "")
                .replace(";", "_")
                .replace(":", "_")
                .replace("\r", "")
                .replace("\n", "")
                .strip();
        return sanitized.isEmpty() ? "file" : sanitized;
    }

    private static String asciiFallback(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        String base = filename;
        String extension = "";
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            base = filename.substring(0, dotIndex);
            extension = filename.substring(dotIndex);
        }

        String asciiBase = base
                .replaceAll("[^\\x20-\\x7E]", "_")
                .replaceAll("[^A-Za-z0-9._ -]", "_")
                .replaceAll("\\s+", " ")
                .strip();
        if (asciiBase.isEmpty()) {
            asciiBase = "download";
        }

        String asciiExtension = extension.replaceAll("[^A-Za-z0-9.]", "");
        if (asciiExtension.equals(".")) {
            asciiExtension = "";
        }
        return asciiBase + asciiExtension;
    }

    private static String encodeRfc5987(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (isRfc5987AttrChar(c)) {
                encoded.append((char) c);
            } else {
                encoded.append('%')
                        .append(Character.toUpperCase(Character.forDigit((c >> 4) & 0xF, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return encoded.toString();
    }

    private static boolean isRfc5987AttrChar(int c) {
        return (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                || "!#$&+-.^_`|~".indexOf(c) >= 0;
    }
}
