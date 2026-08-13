package com.harucut.auth.service;

import com.harucut.auth.email.EmailVerificationService;
import com.harucut.auth.service.RegisterService;
import com.harucut.common.exception.BusinessException;
import com.harucut.auth.dto.RegisterRequest;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.enums.UserRole;
import com.harucut.user.enums.UserStatus;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.user.repository.UserRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class RegisterServiceTest {

    private static final String RAW_PASSWORD = "password123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailVerificationService emailVerificationService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private RegisterService registerService;

    @BeforeEach
    void setUp() {
        registerService = new RegisterService(userRepository, passwordEncoder, emailVerificationService);
    }

    @Nested
    @DisplayName("register")
    class Register {

        @Test
        @DisplayName("비밀번호를 평문이 아니라 인코딩해서 저장한다")
        void encodePassword() {
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, "user@harucut.com")).willReturn(false);

            registerService.register(request());

            User saved = captureSaved();

            assertThat(saved.getPassword()).isNotEqualTo(RAW_PASSWORD);
            Assertions.assertThat(passwordEncoder.matches(RAW_PASSWORD, saved.getPassword())).isTrue();
        }

        @Test
        @DisplayName("가입한 사용자는 HARUCUT / ROLE_USER / ACTIVE에 publicId가 채워진다")
        void fillsDefault() {
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, "user@harucut.com")).willReturn(false);

            registerService.register(request());

            User saved = captureSaved();

            assertThat(saved.getProvider()).isEqualTo(Provider.HARUCUT);
            assertThat(saved.getUserRole()).isEqualTo(UserRole.ROLE_USER);
            assertThat(saved.getUserStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(saved.getProviderId()).isNull();
            assertThat(saved.getPublicId()).hasSize(12);
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 AUTH-030을 던지고 저장하지 않는다")
        void duplicateEmail() {
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, "user@harucut.com")).willReturn(true);

            assertThatThrownBy(() -> registerService.register(request()))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EMAIL_ALREADY_IN_USE);

            then(userRepository).should(never()).save(any(User.class));
        }

        @Test
        @DisplayName("중복 검사를 통과했어도 DB 제약에 걸리면 AUTH-030으로 바꾼다")
        void raceLosesToDbConstraint() {
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, "user@harucut.com")).willReturn(false);
            given(userRepository.save(any(User.class))).willThrow(new DataIntegrityViolationException("uk_usres_provider_email"));

            assertThatThrownBy(() -> registerService.register(request()))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(AuthErrorCode.EMAIL_ALREADY_IN_USE);
        }
    }

    private User captureSaved() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        then(userRepository).should().save(captor.capture());
        return captor.getValue();
    }

    private RegisterRequest request() {
        return new RegisterRequest("user@harucut.com", "하루컷", RAW_PASSWORD);
    }
}