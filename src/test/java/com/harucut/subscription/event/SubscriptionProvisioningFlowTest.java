package com.harucut.subscription.event;

import com.harucut.auth.dto.RegisterRequest;
import com.harucut.auth.email.EmailVerificationService;
import com.harucut.auth.service.RegisterService;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

// 유닛 테스트는 "발행한다"와 "받으면 만든다"를 따로 증명한다. 이 테스트는 그 사이 —
// 실제 발행된 이벤트에 영속 후의 진짜 user id가 실려 리스너까지 닿는지 — 를 잇는다
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("가입 → 구독 프로비저닝 통합")
class SubscriptionProvisioningFlowTest {

    private static final String EMAIL = "subs-flow@harucut.com";

    @Autowired
    private RegisterService registerService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @Test
    @DisplayName("회원가입이 끝나면 그 사용자의 BASIC 구독이 자동 생성된다")
    void registerCreatesBasicSubscription() {
        registerService.register(new RegisterRequest(EMAIL, "구독유저", "password123"));

        User user = userRepository.findByProviderAndEmail(Provider.HARUCUT, EMAIL).orElseThrow();
        UserSubscription subscription = userSubscriptionRepository.findAll().stream()
                .filter(s -> s.getUserId().equals(user.getId()))
                .findFirst()
                .orElseThrow();

        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.BASIC);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodStart()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
        assertThat(subscription.isAutoRenew()).isFalse();
    }
}
