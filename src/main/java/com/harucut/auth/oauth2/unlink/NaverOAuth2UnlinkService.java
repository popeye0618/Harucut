package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.service.UserExitService;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인바운드 unlink — 사용자가 네이버 쪽에서 연동을 끊으면 네이버가 웹훅으로 알려온다.
 * 통보를 놓치면 로그인 수단을 잃은 계정에 과금만 계속되므로, 받은 즉시 탈퇴 요청으로 전환한다.
 */
@Service
@RequiredArgsConstructor
public class NaverOAuth2UnlinkService {

    private final NaverUniqueIdDecryptor decryptor;
    private final UserRepository userRepository;
    private final UserExitService userExitService;

    public void unlink(NaverUnlinkRequest request) {
        String providerId = decryptor.verifyAndDecrypt(request);

        User user = userRepository.findByProviderAndProviderId(Provider.NAVER, providerId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));

        userExitService.requestExit(user.getPublicId());
    }
}
