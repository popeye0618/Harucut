package com.harucut.subscription.service;

import com.harucut.coupon.service.GrantActivationService;
import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.gateway.PgProvider;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionExpirationService")
class SubscriptionExpirationServiceTest {

    private static final Long SUB_ID = 5L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2032, 1, 10, 0, 0);

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private BillingKeyRepository billingKeyRepository;

    @Mock
    private GrantActivationService grantActivationService;

    private SubscriptionExpirationService service;

    @BeforeEach
    void setUp() {
        service = new SubscriptionExpirationService(userSubscriptionRepository, billingKeyRepository,
                grantActivationService);
    }

    @Test
    @DisplayName("만료 대상이면 BASIC으로 강등하고 활성 빌링키를 해제한다")
    void expiresToFreeAndReleasesBillingKeys() {
        UserSubscription subscription = canceledSubscription();
        BillingKey billingKey = BillingKey.issue(USER_ID, PgProvider.MOCK, "bk-1", "1234-****");
        given(userSubscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(subscription));
        given(billingKeyRepository.findAllByUserIdAndStatus(USER_ID, BillingKeyStatus.ACTIVE))
                .willReturn(List.of(billingKey));

        service.expire(SUB_ID, BASE_TIME);

        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.BASIC);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        assertThat(subscription.getCurrentPeriodStart()).isNull();
        assertThat(subscription.getCurrentPeriodEnd()).isNull();
        assertThat(subscription.isAutoRenew()).isFalse();
        assertThat(billingKey.getStatus()).isEqualTo(BillingKeyStatus.DELETED);
        then(grantActivationService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("예약 쿠폰이 있으면 강등 대신 grant를 개시한다 — 빌링키는 건드리지 않는다")
    void reservedCouponConvertsToGrantAndKeepsBillingKey() {
        UserSubscription subscription = canceledSubscription();
        subscription.reserveGrant(77L);
        given(userSubscriptionRepository.findById(SUB_ID)).willReturn(Optional.of(subscription));

        service.expire(SUB_ID, BASE_TIME);

        then(grantActivationService).should().activate(subscription, BASE_TIME);
        then(billingKeyRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("구독이 사라졌으면 아무것도 하지 않는다")
    void missingSubscriptionDoesNothing() {
        given(userSubscriptionRepository.findById(SUB_ID)).willReturn(Optional.empty());

        service.expire(SUB_ID, BASE_TIME);

        then(grantActivationService).shouldHaveNoInteractions();
        then(billingKeyRepository).shouldHaveNoInteractions();
    }

    // ── fixtures ──────────────────────────────

    private UserSubscription canceledSubscription() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS, BASE_TIME.minusMonths(1), BASE_TIME.minusDays(1));
        subscription.cancelAutoRenew();
        return subscription;
    }
}
