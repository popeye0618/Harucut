package com.harucut.storage.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ContentDispositions")
class ContentDispositionsTest {

    @Test
    @DisplayName("한글 파일명은 filename*에 RFC 5987 UTF-8 인코딩으로 담긴다")
    void encodesKoreanFilename() {
        assertThat(ContentDispositions.attachment("한글사진.png"))
                .isEqualTo("attachment; filename=\"____.png\"; "
                        + "filename*=UTF-8''%ED%95%9C%EA%B8%80%EC%82%AC%EC%A7%84.png");
    }

    @Test
    @DisplayName("ASCII 파일명은 filename과 filename*에 같은 값이 담긴다")
    void asciiFilenameAppearsInBoth() {
        assertThat(ContentDispositions.attachment("photo.png"))
                .isEqualTo("attachment; filename=\"photo.png\"; filename*=UTF-8''photo.png");
    }

    @Test
    @DisplayName("공백은 filename에는 그대로, filename*에는 %20으로 담긴다")
    void encodesSpace() {
        assertThat(ContentDispositions.attachment("my photo.png"))
                .isEqualTo("attachment; filename=\"my photo.png\"; filename*=UTF-8''my%20photo.png");
    }

    @Test
    @DisplayName("따옴표·개행·세미콜론이 헤더 값에 살아남지 않는다 — 헤더 인젝션 차단")
    void removesHeaderInjectionCharacters() {
        String result = ContentDispositions.attachment("a\"b;c\r\nd.png");

        assertThat(result)
                .isEqualTo("attachment; filename=\"ab_cd.png\"; filename*=UTF-8''ab_cd.png")
                .doesNotContain("\r")
                .doesNotContain("\n");
    }

    @Test
    @DisplayName("경로 구분자는 파일명으로 취급되지 않게 치환·제거된다")
    void neutralizesPathSeparators() {
        assertThat(ContentDispositions.attachment("../../etc/passwd"))
                .doesNotContain("../")
                .contains("filename=\"");
    }

    @Test
    @DisplayName("빈 이름은 file로 대체된다")
    void blankNameFallsBackToFile() {
        assertThat(ContentDispositions.attachment("  "))
                .isEqualTo("attachment; filename=\"file\"; filename*=UTF-8''file");
    }
}
