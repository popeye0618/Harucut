package com.harucut.storage.util;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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

    @Nested
    @DisplayName("normalizeManagedKey — 실패 없는 관대한 정규화")
    class NormalizeManagedKey {

        @Test
        @DisplayName("우리 버킷 URL(쿼리스트링 포함)은 순수 key가 된다")
        void managedUrlBecomesKey() {
            String presignedUrl = "https://harucut-test.s3.ap-northeast-2.amazonaws.com/" + KEY
                    + "?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Signature=abc";

            assertThat(S3Keys.normalizeManagedKey(presignedUrl)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("외부 URL은 파괴하지 않고 원본 그대로 돌려준다 — normalizeToKey와의 결정적 차이")
        void externalUrlSurvives() {
            String external = "https://cdn.example.com/stickers/heart.png";

            assertThat(S3Keys.normalizeManagedKey(external)).isEqualTo(external);
        }

        @Test
        @DisplayName("s3:// 스킴에서는 key를 추출한다")
        void extractsFromS3Scheme() {
            assertThat(S3Keys.normalizeManagedKey("s3://harucut-test/" + KEY)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("이미 key인 입력은 그대로, 앞 슬래시만 뗀다")
        void keyPassesThrough() {
            assertThat(S3Keys.normalizeManagedKey(KEY)).isEqualTo(KEY);
            assertThat(S3Keys.normalizeManagedKey("/" + KEY)).isEqualTo(KEY);
        }

        @Test
        @DisplayName("null과 빈 값은 예외 없이 그대로 돌려준다")
        void nullAndBlankPassThrough() {
            assertThat(S3Keys.normalizeManagedKey(null)).isNull();
            assertThat(S3Keys.normalizeManagedKey("  ")).isEqualTo("  ");
        }

        @Test
        @DisplayName("깨진 URL도 예외 없이 원본을 돌려준다 — 이 연산에 실패는 없다")
        void malformedUrlSurvives() {
            String broken = "https://harucut test/브로큰 url";

            assertThat(S3Keys.normalizeManagedKey(broken)).isEqualTo(broken);
        }
    }

    @Nested
    @DisplayName("isManagedKey")
    class IsManagedKey {

        @Test
        @DisplayName("uploads/ 아래만 관리 대상이다")
        void onlyUploadRootIsManaged() {
            assertThat(S3Keys.isManagedKey(KEY)).isTrue();
            assertThat(S3Keys.isManagedKey("https://cdn.example.com/x.png")).isFalse();
            assertThat(S3Keys.isManagedKey("static/stickers/heart.png")).isFalse();
            assertThat(S3Keys.isManagedKey(null)).isFalse();
        }
    }

    @Nested
    @DisplayName("assertOwnedBy")
    class AssertOwnedBy {

        private static final String OWNER = "AbCdEf12Gh";
        private static final String OTHER = "ZzZzZzZzZz";

        @Test
        @DisplayName("내 폴더의 key는 통과한다")
        void ownKeyPasses() {
            assertThatCode(() -> S3Keys.assertOwnedBy(KEY, OWNER)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("남의 폴더의 key는 403이다")
        void otherUsersKeyIsForbidden() {
            String othersKey = S3Keys.userRoot(OTHER) + "fourcuts/job-1.png";

            assertThatThrownBy(() -> S3Keys.assertOwnedBy(othersKey, OWNER))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", GlobalErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("URL로 감싸도 정규화된 key로 검사하므로 우회되지 않는다")
        void urlWrappedOtherKeyIsForbidden() {
            String wrapped = "https://harucut-test.s3.amazonaws.com/"
                    + S3Keys.userRoot(OTHER) + "profile/a.png";

            assertThatThrownBy(() -> S3Keys.assertOwnedBy(wrapped, OWNER))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("경로 조작(.. · // · 역슬래시)은 내 폴더로 시작해도 막는다")
        void traversalIsForbidden() {
            assertThatThrownBy(() -> S3Keys.assertOwnedBy(
                    S3Keys.userRoot(OWNER) + "../" + OTHER + "/profile/a.png", OWNER))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> S3Keys.assertOwnedBy(
                    S3Keys.userRoot(OWNER) + "profile//a.png", OWNER))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> S3Keys.assertOwnedBy(
                    S3Keys.userRoot(OWNER) + "profile\\a.png", OWNER))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("우리 파일이 아닌 값은 소유권을 따지지 않는다 — 정적 경로·외부 URL·텍스트 본문")
        void unmanagedValuesPass() {
            assertThatCode(() -> {
                S3Keys.assertOwnedBy("/static/stickers/heart.png", OWNER);
                S3Keys.assertOwnedBy("https://cdn.example.com/heart.png", OWNER);
                S3Keys.assertOwnedBy("봄 여행 4컷", OWNER);
                S3Keys.assertOwnedBy(null, OWNER);
                S3Keys.assertOwnedBy("  ", OWNER);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("publicId가 다른 사용자의 접두사여도 폴더 경계에서 갈린다")
        void prefixCollisionIsNotPossible() {
            // userRoot 가 끝에 /를 붙이므로 "Ab"와 "AbCdEf12Gh"가 섞이지 않는다
            assertThatThrownBy(() -> S3Keys.assertOwnedBy(
                    "uploads/users/AbCdEf12GhXX/profile/a.png", OWNER))
                    .isInstanceOf(BusinessException.class);
        }
    }
}
