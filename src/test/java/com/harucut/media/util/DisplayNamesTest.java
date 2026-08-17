package com.harucut.media.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DisplayNames")
class DisplayNamesTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 7, 22, 10, 0);
    private static final String KEY = "uploads/users/abc/fourcuts/550e8400.png";

    private static String resolve(String preferredName) {
        return DisplayNames.resolve(preferredName, KEY, BASE_TIME);
    }

    @Nested
    @DisplayName("위험한 문자 제거")
    class DangerousCharacters {

        @Test
        @DisplayName("경로 순회 입력은 마지막 조각만 남는다")
        void pathTraversal() {
            assertThat(resolve("../../etc/passwd")).isEqualTo("passwd.png");
        }

        @Test
        @DisplayName("윈도우 경로도 파일명만 남는다")
        void windowsPath() {
            assertThat(resolve("C:\\사진\\내사진.png")).isEqualTo("내사진.png");
        }

        @Test
        @DisplayName("따옴표가 제거된다 — Content-Disposition 값을 일찍 닫는 문자")
        void quotes() {
            assertThat(resolve("a\"b.png")).isEqualTo("ab.png");
        }

        @Test
        @DisplayName("개행이 제거된다 — 헤더에 새 줄을 주입하는 문자")
        void newlines() {
            assertThat(resolve("a\r\nb.png")).isEqualTo("ab.png");
        }

        @Test
        @DisplayName("제어문자가 제거된다")
        void controlCharacters() {
            assertThat(resolve("a\tb\u0000c.png")).isEqualTo("abc.png");
        }

        @Test
        @DisplayName("꼬리 슬래시로 끝나는 입력에 슬래시가 남지 않는다")
        void trailingSlash() {
            assertThat(resolve("사진/")).isEqualTo("사진.png");
        }

        @Test
        @DisplayName("연속 공백은 한 칸으로 줄어든다")
        void collapsesWhitespace() {
            assertThat(resolve("내   사진")).isEqualTo("내 사진.png");
        }
    }

    @Nested
    @DisplayName("확장자")
    class Extensions {

        @Test
        @DisplayName("이름에 확장자가 없어도 key의 확장자가 붙는다")
        void appendsKeyExtension() {
            assertThat(resolve("my_photo")).isEqualTo("my_photo.png");
        }

        @Test
        @DisplayName("이름의 확장자는 버리고 key의 확장자로 바꾼다")
        void replacesNameExtension() {
            assertThat(resolve("악성.exe")).isEqualTo("악성.png");
        }

        @Test
        @DisplayName("key의 대문자 확장자는 소문자가 된다")
        void lowercased() {
            assertThat(DisplayNames.resolve("이름", "uploads/a.PNG", BASE_TIME))
                    .isEqualTo("이름.png");
        }

        @Test
        @DisplayName("key에 확장자가 없으면 안 붙인다")
        void keyWithoutExtension() {
            assertThat(DisplayNames.resolve("이름", "uploads/noext", BASE_TIME))
                    .isEqualTo("이름");
        }

        @Test
        @DisplayName("10자를 넘는 확장자는 확장자로 인정하지 않는다")
        void tooLongExtension() {
            assertThat(DisplayNames.resolve("이름", "uploads/a.verylongext1", BASE_TIME))
                    .isEqualTo("이름");
        }

        @Test
        @DisplayName("맨 앞의 점은 확장자가 아니라 이름의 일부다")
        void leadingDot() {
            assertThat(resolve(".hidden")).isEqualTo(".hidden.png");
        }
    }

    @Nested
    @DisplayName("빈 이름 fallback")
    class Fallback {

        @Test
        @DisplayName("이름이 null이면 harucut_시각 이름이 된다")
        void nullName() {
            assertThat(resolve(null)).isEqualTo("harucut_20260722_100000.png");
        }

        @Test
        @DisplayName("공백뿐인 이름도 fallback이다")
        void blankName() {
            assertThat(resolve("   ")).isEqualTo("harucut_20260722_100000.png");
        }

        @Test
        @DisplayName("정제 후 아무것도 안 남아도 fallback이다")
        void nothingLeftAfterSanitize() {
            assertThat(resolve("\"\r\n///")).isEqualTo("harucut_20260722_100000.png");
        }

        @Test
        @DisplayName("baseTime이 없으면 즉시 NPE다 — 숨은 now()를 두지 않는 계약")
        void baseTimeRequired() {
            assertThatThrownBy(() -> DisplayNames.resolve("이름", KEY, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("255자 절단")
    class Truncation {

        @Test
        @DisplayName("긴 이름은 255자로 잘리고 확장자는 살아남는다")
        void truncatesPreservingExtension() {
            String result = resolve("가".repeat(300));

            assertThat(result).hasSize(255).endsWith(".png");
        }

        @Test
        @DisplayName("정확히 255자면 안 잘린다 — 경계")
        void exactLimit() {
            String base = "a".repeat(251);   // 251 + ".png" 4자 = 255

            assertThat(resolve(base)).isEqualTo(base + ".png");
        }

        @Test
        @DisplayName("256자가 되는 순간 base에서 한 글자가 잘린다")
        void oneOverLimit() {
            String base = "a".repeat(252);   // 252 + 4 = 256

            assertThat(resolve(base)).isEqualTo("a".repeat(251) + ".png");
        }
    }
}
