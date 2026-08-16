package com.harucut.storage.enums;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ContentType")
class ContentTypeTest {

    @Nested
    @DisplayName("validateExtension")
    class ValidateExtension {

        @Test
        @DisplayName("허용 확장자면 정규화된 값을 돌려준다")
        void returnsNormalizedExtension() {
            assertThat(ContentType.PNG.validateExtension("png")).isEqualTo("png");
        }

        @Test
        @DisplayName("대문자·점 붙은 확장자(.PNG)도 소문자 정규화 후 통과한다")
        void normalizesUpperCaseAndDot() {
            assertThat(ContentType.PNG.validateExtension(".PNG")).isEqualTo("png");
        }

        @Test
        @DisplayName("JPEG는 jpg와 jpeg 둘 다 받는다")
        void jpegAcceptsBothExtensions() {
            assertThat(ContentType.JPEG.validateExtension("jpg")).isEqualTo("jpg");
            assertThat(ContentType.JPEG.validateExtension("jpeg")).isEqualTo("jpeg");
        }

        @Test
        @DisplayName("MIME과 확장자가 어긋나면(PNG + jpg) GEN-051을 던진다")
        void mimeExtensionMismatch() {
            assertThatThrownBy(() -> ContentType.PNG.validateExtension("jpg"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("지원하지 않는 확장자(exe)는 GEN-051을 던진다")
        void unsupportedExtension() {
            assertThatThrownBy(() -> ContentType.PNG.validateExtension("exe"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("빈 확장자는 GEN-051을 던진다")
        void blankExtension() {
            assertThatThrownBy(() -> ContentType.PNG.validateExtension(""))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }

        @Test
        @DisplayName("null 확장자는 GEN-051을 던진다")
        void nullExtension() {
            assertThatThrownBy(() -> ContentType.PNG.validateExtension(null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
    }
}
