package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.service.UserExitService;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("NaverOAuth2UnlinkService")
class NaverOAuth2UnlinkServiceTest {

    private static final NaverUnlinkRequest REQUEST =
            new NaverUnlinkRequest("client-id", "encrypted", "1755450000", "signature");

    @Mock
    private NaverUniqueIdDecryptor decryptor;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserExitService userExitService;

    private NaverOAuth2UnlinkService service;

    @BeforeEach
    void setUp() {
        service = new NaverOAuth2UnlinkService(decryptor, userRepository, userExitService);
    }

    @Test
    @DisplayName("복호화한 providerId의 사용자를 탈퇴 요청으로 전환한다")
    void convertsNotificationToExitRequest() {
        User user = UserFixtures.socialUser("naver@harucut.com", Provider.NAVER, "naver-123");
        given(decryptor.verifyAndDecrypt(REQUEST)).willReturn("naver-123");
        given(userRepository.findByProviderAndProviderId(Provider.NAVER, "naver-123"))
                .willReturn(Optional.of(user));

        service.unlink(REQUEST);

        then(userExitService).should().requestExit(user.getPublicId());
    }

    @Test
    @DisplayName("해당 사용자가 없으면 GEN-031이고 탈퇴 요청은 없다")
    void unknownProviderId() {
        given(decryptor.verifyAndDecrypt(REQUEST)).willReturn("naver-999");
        given(userRepository.findByProviderAndProviderId(Provider.NAVER, "naver-999"))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.unlink(REQUEST))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.NOT_FOUND);

        then(userExitService).shouldHaveNoInteractions();
    }
}
