package com.harucut.user.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
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

    public UserInfoResponse getUserInfo(String publicId) {
        User user = getUser(publicId);
        return UserInfoResponse.from(user, "BASIC", 0);
    }

    @Transactional
    public void changeUsername(String publicId, String username) {
        User user = getUser(publicId);
        user.changeUsername(username);
    }

    private User getUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));
    }
}
