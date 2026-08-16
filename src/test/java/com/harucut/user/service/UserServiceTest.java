package com.harucut.user.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.storage.service.FileStorageService;
import com.harucut.support.UserFixtures;
import com.harucut.user.dto.UserInfoResponse;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    private static final String UNKNOWN_PUBLIC_ID = "NoSuchUser1";

    @Mock
    private UserRepository userRepository;

    @Mock
    private FileStorageService fileStorageService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, fileStorageService);
    }

    @Nested
    @DisplayName("getUserInfo")
    class GetUserInfo {

        @Test
        @DisplayName("id에 PK가 아니라 publicId를 담는다")
        void returnsPublicIdAsId() {
            User user = stubFound(localUser());

            UserInfoResponse response = userService.getUserInfo(user.getPublicId());

            assertThat(response.id()).isEqualTo(user.getPublicId());
        }

        @Test
        @DisplayName("profileUrl에 저장된 key가 아니라 presigned URL을 담는다 — 기본 이미지 key도 presign 대상이다")
        void presignsProfileUrl() {
            User user = stubFound(localUser());
            given(fileStorageService.generatePresignedGetUrl(user.getProfileImageUrl()))
                    .willReturn("https://signed.example/profile.png");

            UserInfoResponse response = userService.getUserInfo(user.getPublicId());

            assertThat(response.profileUrl()).isEqualTo("https://signed.example/profile.png");
        }

        @Test
        @DisplayName("구독이 없는 동안 planTier는 BASIC, monthlyPrice는 0으로 고정된다")
        void returnsFixedBasicPlan() {
            User user = stubFound(localUser());

            UserInfoResponse response = userService.getUserInfo(user.getPublicId());

            assertThat(response.planTier()).isEqualTo("BASIC");
            assertThat(response.monthlyPrice()).isZero();
        }

        @Test
        @DisplayName("loginPlatform은 사용자의 provider 이름이다")
        void mapsProviderToLoginPlatform() {
            User user = stubFound(UserFixtures.socialUser("user@harucut.com", Provider.GOOGLE));

            UserInfoResponse response = userService.getUserInfo(user.getPublicId());

            assertThat(response.loginPlatform()).isEqualTo("GOOGLE");
        }

        @Test
        @DisplayName("publicId로 사용자를 찾지 못하면 GEN-031을 던진다")
        void notFound() {
            stubMissing();

            assertThatThrownBy(() -> userService.getUserInfo(UNKNOWN_PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("changeUsername")
    class ChangeUsername {

        @Test
        @DisplayName("엔티티의 username을 넘겨받은 값으로 바꾼다")
        void changesUsername() {
            User user = stubFound(localUser());

            userService.changeUsername(user.getPublicId(), "새이름");

            assertThat(user.getUsername()).isEqualTo("새이름");
        }

        @Test
        @DisplayName("publicId로 사용자를 찾지 못하면 GEN-031을 던진다")
        void notFound() {
            stubMissing();

            assertThatThrownBy(() -> userService.changeUsername(UNKNOWN_PUBLIC_ID, "새이름"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("changeProfileImage")
    class ChangeProfileImage {

        @Test
        @DisplayName("순수 key가 들어오면 그대로 저장한다")
        void storesPlainKey() {
            User user = stubFound(localUser());
            String key = "uploads/users/" + user.getPublicId() + "/profile/a1b2c3.png";

            userService.changeProfileImage(user.getPublicId(), key);

            assertThat(user.getProfileImageUrl()).isEqualTo(key);
        }

        @Test
        @DisplayName("쿼리스트링 붙은 presigned URL이 들어와도 순수 key로 정규화해 저장한다")
        void normalizesUrlToKey() {
            User user = stubFound(localUser());
            String key = "uploads/users/" + user.getPublicId() + "/profile/a1b2c3.png";

            userService.changeProfileImage(user.getPublicId(),
                    "https://harucut-test.s3.ap-northeast-2.amazonaws.com/" + key + "?X-Amz-Signature=abc");

            assertThat(user.getProfileImageUrl()).isEqualTo(key);
        }

        @Test
        @DisplayName("다른 사용자 prefix의 key면 GEN-021을 던지고 저장하지 않는다")
        void rejectsForeignKey() {
            User user = stubFound(localUser());
            String before = user.getProfileImageUrl();

            assertThatThrownBy(() -> userService.changeProfileImage(user.getPublicId(),
                    "uploads/users/SomeoneElse1/profile/a.png"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.FORBIDDEN);

            assertThat(user.getProfileImageUrl()).isEqualTo(before);
        }

        @Test
        @DisplayName("URL로 감싼 남의 key도 정규화 후 검사에 걸린다 — 원본 문자열 검사였다면 통과했을 입력이다")
        void rejectsForeignKeyWrappedInUrl() {
            User user = stubFound(localUser());

            assertThatThrownBy(() -> userService.changeProfileImage(user.getPublicId(),
                    "https://harucut-test.s3.ap-northeast-2.amazonaws.com/uploads/users/SomeoneElse1/profile/a.png"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.FORBIDDEN);
        }

        @Test
        @DisplayName("publicId로 사용자를 찾지 못하면 GEN-031을 던진다")
        void notFound() {
            stubMissing();

            assertThatThrownBy(() -> userService.changeProfileImage(UNKNOWN_PUBLIC_ID, "uploads/a.png"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.NOT_FOUND);
        }
    }

    private User localUser() {
        return UserFixtures.localUser("user@harucut.com", "encoded");
    }

    private User stubFound(User user) {
        given(userRepository.findByPublicId(user.getPublicId())).willReturn(Optional.of(user));
        return user;
    }

    private void stubMissing() {
        given(userRepository.findByPublicId(UNKNOWN_PUBLIC_ID)).willReturn(Optional.empty());
    }
}
