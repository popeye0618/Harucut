package com.harucut.storage.util;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("S3Keys")
class S3KeysTest {

    private static final String KEY = "uploads/users/AbCdEf12Gh/profile/a1b2c3.png";

    @Nested
    @DisplayName("userRoot")
    class UserRoot {

        @Test
        @DisplayName("uploads/users/{publicId}/ 형태를 만든다")
        void buildsUserRoot() {
            assertThat(S3Keys.userRoot("AbCdEf12Gh")).isEqualTo("uploads/users/AbCdEf12Gh/");
        }
    }

    @Nested
    @DisplayName("normalizeToKey")
    class NormalizeToKey {

        @Test
        @DisplayName("전체 URL, 쿼리스트링 붙은 URL, 이미 key인 입력 전부 같은 결과가 나온다")
        void allInputFormsYieldSameKey() {
            String fullUrl = "https://harucut-test.s3.ap-northeast-2.amazonaws.com/" + KEY;
            String presignedUrl = fullUrl + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc";

            assertThat(S3Keys.normalizeToKey(fullUrl))
                    .isEqualTo(S3Keys.normalizeToKey(presignedUrl))
                    .isEqualTo(S3Keys.normalizeToKey(KEY))
                    .isEqualTo(KEY);
        }

        @Test
        @DisplayName("s3:// 스킴 URL에서도 key를 추출한다")
        void extractsFromS3Scheme() {
            assertThat(S3Keys.normalizeToKey("s3://harucut-test/" + KEY)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("앞에 슬래시가 붙은 key는 떼고 돌려준다")
        void stripsLeadingSlash() {
            assertThat(S3Keys.normalizeToKey("/" + KEY)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("빈 입력은 GEN-002를 던진다")
        void blankInput() {
            assertThatThrownBy(() -> S3Keys.normalizeToKey("  "))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("경로 없는 URL(호스트뿐)은 GEN-002를 던진다")
        void urlWithoutPath() {
            assertThatThrownBy(() -> S3Keys.normalizeToKey("https://harucut-test.s3.amazonaws.com/"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        @Test
        @DisplayName("깨진 URL은 500이 아니라 GEN-002를 던진다")
        void malformedUrl() {
            assertThatThrownBy(() -> S3Keys.normalizeToKey("https://harucut test/브로큰 url"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
