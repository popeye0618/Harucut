package com.harucut.auth.email;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import com.harucut.common.mail.MailService;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.IContext;

import java.time.Duration;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "user@harucut.com";
    private static final String CODE = "ABC234";
    private static final String HTML = "<html>ABC234</html>";
    private static final String TEMPLATE = "mail/verification-code";

    private static final String CODE_KEY = "email:code:" + EMAIL;
    private static final String VERIFIED_KEY = "email:verified:" + EMAIL;

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration VERIFIED_TTL = Duration.ofMinutes(10);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private VerificationCodeGenerator generator;

    @Mock
    private UserRepository userRepository;

    @Mock
    private MailService mailService;

    @Mock
    private ITemplateEngine templateEngine;

    @Mock
    private EmailRateLimit emailRateLimit;

    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        service = new EmailVerificationService(
                generator,
                new EmailVerificationRepository(redisTemplate),
                emailRateLimit,
                userRepository,
                mailService,
                templateEngine
        );
    }

    @Nested
    @DisplayName("sendVerificationCode")
    class SendVerificationCode {

        @Test
        @DisplayName("생성한 코드를 email:code 키에 5분 TTL로 저장한다")
        void storesCodeWithTtl() {
            givenCooldownAcquired(EMAIL);
            givenValueOperations();
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, EMAIL)).willReturn(false);
            given(generator.generate()).willReturn(CODE);

            service.sendVerificationCode(EMAIL);

            then(valueOperations).should().set(CODE_KEY, CODE, CODE_TTL);
            then(emailRateLimit).should(never()).releaseCooldown(any());
        }

        @Test
        @DisplayName("렌더링한 템플릿 본문으로 메일을 발송한다")
        void sendsRenderedTemplate() {
            givenCooldownAcquired(EMAIL);
            givenValueOperations();
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, EMAIL)).willReturn(false);
            given(templateEngine.process(eq(TEMPLATE), any(IContext.class))).willReturn(HTML);

            service.sendVerificationCode(EMAIL);

            then(mailService).should().sendHtml(eq(EMAIL), any(), eq(HTML));
        }

        @Test
        @DisplayName("대문자 이메일로 들어와도 email:code 키는 소문자로 만든다")
        void lowercasesKey() {
            String mixedCase = "User@Harucut.com";

            givenCooldownAcquired(mixedCase);
            givenValueOperations();
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, mixedCase)).willReturn(false);
            given(generator.generate()).willReturn(CODE);

            service.sendVerificationCode(mixedCase);

            then(valueOperations).should().set(CODE_KEY, CODE, CODE_TTL);
        }

        @Test
        @DisplayName("쿨다운 중이면 AUTH-040을 던지고 메일을 보내지 않는다")
        void rejectedWhileCooldown() {
            given(emailRateLimit.tryAcquireCooldown(EMAIL)).willReturn(false);

            assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.TOO_MANY_REQUESTS);

            then(redisTemplate).shouldHaveNoInteractions();
            then(userRepository).shouldHaveNoInteractions();
            then(mailService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 가입된 이메일이면 AUTH-030을 던지고 코드를 저장하지 않는다")
        void rejectsAlreadyRegistered() {
            givenCooldownAcquired(EMAIL);
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, EMAIL)).willReturn(true);

            assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EMAIL_ALREADY_IN_USE);

            then(redisTemplate).shouldHaveNoInteractions();
            then(mailService).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("SMTP 전송이 실패하면 MailException을 AUTH-090으로 감싼다")
        void wrapsMailFailure() {
            givenCooldownAcquired(EMAIL);
            givenValueOperations();
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, EMAIL)).willReturn(false);
            willThrow(new MailSendException("smtp down"))
                    .given(mailService).sendHtml(any(), any(), any());

            assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EMAIL_SEND_FAILED);
        }

        @Test
        @DisplayName("SMTP 전송이 실패하면 쿨다운을 풀어 즉시 재요청을 허용한다")
        void releasesCooldownOnMailFailure() {
            givenCooldownAcquired(EMAIL);
            givenValueOperations();
            given(userRepository.existsByProviderAndEmail(Provider.HARUCUT, EMAIL)).willReturn(false);
            willThrow(new MailSendException("smtp down"))
                    .given(mailService).sendHtml(any(), any(), any());

            assertThatThrownBy(() -> service.sendVerificationCode(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EMAIL_SEND_FAILED);

            then(emailRateLimit).should().releaseCooldown(EMAIL);
        }
    }

    @Nested
    @DisplayName("verifyCode")
    class VerifyCode {
        @Test
        @DisplayName("코드가 일치하면 code를 지우고 verified를 10분 TTL로 심는다")
        void promotesToVerified() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);

            service.verifyCode(EMAIL, CODE);

            then(redisTemplate).should().delete(CODE_KEY);
            then(valueOperations).should().set(VERIFIED_KEY, "VERIFIED", VERIFIED_TTL);
        }

        @Test
        @DisplayName("소문자로 입력해도 검증에 성공한다")
        void verifiesIgnoringCase() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);

            service.verifyCode(EMAIL, CODE.toLowerCase());

            then(valueOperations).should().set(VERIFIED_KEY, "VERIFIED", VERIFIED_TTL);
        }

        @Test
        @DisplayName("코드가 틀리면 AUTH-003을 던지되 재시도할 수 있게 code를 남긴다")
        void wrongCodeKeepsCodeForRetry() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(CODE);

            assertThatThrownBy(() -> service.verifyCode(EMAIL, "ZZZZZZ"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_VERIFICATION_CODE);

            then(redisTemplate).should(never()).delete(CODE_KEY);
            then(valueOperations).should(never()).set(eq(VERIFIED_KEY), any(), any(Duration.class));
        }

        @Test
        @DisplayName("코드가 만료되거나 발급된 적이 없으면 AUTH-003을 던진다")
        void missingCode() {
            given(redisTemplate.opsForValue()).willReturn(valueOperations);
            given(valueOperations.get(CODE_KEY)).willReturn(null);

            assertThatThrownBy(() -> service.verifyCode(EMAIL, CODE))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_VERIFICATION_CODE);

            then(valueOperations).should(never()).set(eq(VERIFIED_KEY), any(), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("consumeVerified")
    class ConsumeVerified {

        @Test
        @DisplayName("verified 키가 있으면 소비하고 통과시킨다")
        void consumesFlag() {
            given(redisTemplate.delete(VERIFIED_KEY)).willReturn(true);

            assertThatCode(() -> service.consumeVerified(EMAIL))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("verified 키가 없으면 AUTH-004를 던진다")
        void rejectsUnverified() {
            given(redisTemplate.delete(VERIFIED_KEY)).willReturn(false);

            assertThatThrownBy(() -> service.consumeVerified(EMAIL))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.EMAIL_NOT_VERIFIED);
        }
    }

    private void givenCooldownAcquired(String email) {
        given(emailRateLimit.tryAcquireCooldown(email)).willReturn(true);
    }

    private void givenValueOperations() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }
}