package com.harucut.user.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.storage.service.FileStorageService;
import com.harucut.storage.util.S3Keys;
import com.harucut.user.dto.UserInfoResponse;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    public UserInfoResponse getUserInfo(String publicId) {
        User user = getUser(publicId);
        String profileUrl = fileStorageService.generatePresignedGetUrl(user.getProfileImageUrl());
        return UserInfoResponse.from(user, profileUrl, "BASIC", 0);
    }

    @Transactional
    public void changeUsername(String publicId, String username) {
        User user = getUser(publicId);
        user.changeUsername(username);
    }

    // 검사 대상은 원본 입력이 아니라 정규화된 key다 — URL로 감싼 남의 key가 원본 검사는 통과한다
    @Transactional
    public void changeProfileImage(String publicId, String s3Key) {
        User user = getUser(publicId);
        String key = S3Keys.normalizeToKey(s3Key);
        if (!key.startsWith(S3Keys.userRoot(publicId))) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        user.changeProfileImageUrl(key);
    }

    private User getUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));
    }
}
