package com.harucut.user.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository);
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
        @DisplayName("profileUrl은 presigned URL이 아니라 저장된 S3 key 원본이다")
        void returnsRawProfileKey() {
            User user = stubFound(localUser());

            UserInfoResponse response = userService.getUserInfo(user.getPublicId());

            assertThat(response.profileUrl())
                    .isEqualTo(user.getProfileImageUrl())
                    .doesNotStartWith("http");
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
