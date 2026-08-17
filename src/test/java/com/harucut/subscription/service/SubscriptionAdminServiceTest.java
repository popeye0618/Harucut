package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.subscription.dto.SubscriptionAdminResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.exception.SubscriptionErrorCode;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionAdminService")
class SubscriptionAdminServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private SubscriptionAdminService adminService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        adminService = new SubscriptionAdminService(userSubscriptionRepository, fixedClock);
    }

    @Test
    @DisplayName("만료 전에는 결제한 등급과 실제 적용 등급이 같다")
    void activePaidShowsSameTiers() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS,
                LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));

        SubscriptionAdminResponse response = adminService.getSubscription(USER_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
        assertThat(response.effectiveTier()).isEqualTo(PlanTier.PLUS);
        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.currentPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
        assertThat(response.autoRenew()).isTrue();
    }

    @Test
    @DisplayName("공백기 — 결제한 등급은 PLUS인데 실제 적용 등급은 BASIC으로 나란히 보인다")
    void gapPeriodShowsBothTiers() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));

        SubscriptionAdminResponse response = adminService.getSubscription(USER_ID);

        // 이 두 줄의 차이가 곧 진단이다 — 이 API를 사용자용과 따로 만든 이유
        assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
        assertThat(response.effectiveTier()).isEqualTo(PlanTier.BASIC);
    }

    @Test
    @DisplayName("구독 행이 없으면 SUBS-004를 던진다 — 폴백하지 않는다")
    void noSubscription() {
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.getSubscription(USER_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION);
    }
}
