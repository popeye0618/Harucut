package com.harucut.auth.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.auth.dto.RegisterRequest;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByProviderAndEmail(Provider.HARUCUT, request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

        User user = User.localUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.username()
        );

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }
    }
}
