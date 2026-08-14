package com.harucut.auth.service;

import com.harucut.auth.email.EmailVerificationService;
import com.harucut.common.exception.BusinessException;
import com.harucut.auth.dto.RegisterRequest;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByProviderAndEmail(Provider.HARUCUT, request.email())) {
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

        emailVerificationService.requireVerified(request.email());

        User user = User.localUser(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.username()
        );

        try {
            userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            log.warn("[register] 저장 중 제약 위반. publicId={}", user.getPublicId(), e);
            throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }

        emailVerificationService.clearVerified(request.email());
    }
}
