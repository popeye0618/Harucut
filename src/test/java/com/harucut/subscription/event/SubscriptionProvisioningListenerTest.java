package com.harucut.subscription.event;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.event.UserRegisterEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionProvisioningListener")
class SubscriptionProvisioningListenerTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private SubscriptionProvisioningListener listener;

    @BeforeEach
    void setUp() {
        listener = new SubscriptionProvisioningListener(userSubscriptionRepository);
    }

    @Test
    @DisplayName("구독이 없으면 BASIC/ACTIVE, 주기 없음, 자동갱신 없음으로 저장한다")
    void createsBasicWhenAbsent() {
        given(userSubscriptionRepository.existsByUserId(USER_ID)).willReturn(false);

        listener.handleUserRegistered(new UserRegisterEvent(USER_ID));

        ArgumentCaptor<UserSubscription> captor = ArgumentCaptor.captor();
        then(userSubscriptionRepository).should().save(captor.capture());
        UserSubscription saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getPlanTier()).isEqualTo(PlanTier.BASIC);
        assertThat(saved.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(saved.getCurrentPeriodStart()).isNull();
        assertThat(saved.getCurrentPeriodEnd()).isNull();
        assertThat(saved.isAutoRenew()).isFalse();
    }

    @Test
    @DisplayName("이미 구독이 있으면 아무것도 저장하지 않는다 — 멱등")
    void skipsWhenAlreadyExists() {
        given(userSubscriptionRepository.existsByUserId(USER_ID)).willReturn(true);

        listener.handleUserRegistered(new UserRegisterEvent(USER_ID));

        then(userSubscriptionRepository).should(never()).save(any(UserSubscription.class));
    }
}
