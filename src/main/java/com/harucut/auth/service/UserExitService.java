package com.harucut.auth.service;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import com.harucut.storage.event.S3DeleteEvent;
import com.harucut.storage.util.S3Keys;
import com.harucut.user.entity.User;
import com.harucut.user.enums.UserStatus;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserExitService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;
    private final List<UserDeletionHandler> handlers;
    private final ApplicationEventPublisher eventPublisher;

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

    @Transactional
    public void exit(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        if (user.getUserStatus() != UserStatus.DELETED_REQUESTED) {
            throw new BusinessException(AuthErrorCode.NOT_DELETION_TARGET);
        }

        String publicId = user.getPublicId();
        String profileImageKey = user.getProfileImageUrl();

        handlers.forEach(handler -> handler.handleUserDeletion(userId));

        // 핸들러들의 벌크 삭제(clearAutomatically)가 컨텍스트를 비웠다 — 위의 user는 유령이라
        // 그대로 delete()하면 익명화가 조용히 증발한다. 재로드가 필수다.
        User managedUser = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));

        // TODO(Phase 13): provider별 소셜 연동 해제(unlink)가 여기 들어간다
        refreshTokenService.revoke(publicId);
        managedUser.delete();

        if (S3Keys.isManagedKey(profileImageKey)) {
            eventPublisher.publishEvent(new S3DeleteEvent(List.of(profileImageKey)));
        }
    }

    private User findUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.USER_NOT_FOUND));
    }
}
