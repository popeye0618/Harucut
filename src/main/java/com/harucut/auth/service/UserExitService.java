package com.harucut.auth.service;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import com.harucut.user.entity.User;
import com.harucut.user.enums.UserStatus;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserExitService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    @Transactional
    public void requestExit(String publicId) {
        User user = findUser(publicId);
        user.deleteRequested(LocalDateTime.now(clock));
        refreshTokenService.revoke(publicId);
    }

    @Transactional
    public void reActivate(String publicId) {
        User user = findUser(publicId);
        if (user.getUserStatus() != UserStatus.DELETED_REQUESTED) {
            throw new BusinessException(AuthErrorCode.NOT_DELETION_TARGET);
        }
        refreshTokenService.revoke(publicId);
        user.reActivate();
    }

    private User findUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
    }
}
