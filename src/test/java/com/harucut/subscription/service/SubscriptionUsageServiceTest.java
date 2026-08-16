package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.subscription.dto.SubscriptionUsageResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.port.FrameCountPort;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionUsageService")
class SubscriptionUsageServiceTest {

    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final Long USER_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private FrameCountPort frameCountPort;

    private SubscriptionUsageService usageService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        usageService = new SubscriptionUsageService(
                userRepository, userSubscriptionRepository, frameCountPort, fixedClock);
    }

    @Test
    @DisplayName("BASIC — 한도 0, 잔여 0, 무제한 아님")
    void basicUsage() {
        givenTier(PlanTier.BASIC);
        given(frameCountPort.countByUserId(USER_ID)).willReturn(0);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.BASIC);
        assertThat(response.frameRetentionLimit()).isEqualTo(0);
        assertThat(response.frameRetentionUsedCount()).isEqualTo(0);
        assertThat(response.frameRetentionRemainingCount()).isEqualTo(0);
        assertThat(response.frameRetentionUnlimited()).isFalse();
    }

    @Test
    @DisplayName("PLUS — 한도 3에 1개 사용이면 잔여 2다")
    void plusUsage() {
        givenTier(PlanTier.PLUS);
        given(frameCountPort.countByUserId(USER_ID)).willReturn(1);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
        assertThat(response.frameRetentionLimit()).isEqualTo(3);
        assertThat(response.frameRetentionUsedCount()).isEqualTo(1);
        assertThat(response.frameRetentionRemainingCount()).isEqualTo(2);
        assertThat(response.frameRetentionUnlimited()).isFalse();
    }

    @Test
    @DisplayName("PLUS — 한도보다 많이 보유해도(강등 시나리오) 잔여는 음수가 아니라 0이다")
    void plusOverCapClampsRemaining() {
        givenTier(PlanTier.PLUS);
        given(frameCountPort.countByUserId(USER_ID)).willReturn(5);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.frameRetentionUsedCount()).isEqualTo(5);
        assertThat(response.frameRetentionRemainingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("PRO — 한도와 잔여가 -1(무제한 규약)이고 unlimited가 true다")
    void proUsage() {
        givenTier(PlanTier.PRO);
        given(frameCountPort.countByUserId(USER_ID)).willReturn(10);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.PRO);
        assertThat(response.frameRetentionLimit()).isEqualTo(-1);
        assertThat(response.frameRetentionUsedCount()).isEqualTo(10);
        assertThat(response.frameRetentionRemainingCount()).isEqualTo(-1);
        assertThat(response.frameRetentionUnlimited()).isTrue();
    }

    @Test
    @DisplayName("공백기 — 주기가 끝난 PLUS는 BASIC 값(한도 0)으로 응답한다")
    void gapPeriodShowsBasicUsage() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS,
                LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
        givenUser();
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
        given(frameCountPort.countByUserId(USER_ID)).willReturn(2);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.BASIC);
        assertThat(response.frameRetentionLimit()).isEqualTo(0);
        assertThat(response.frameRetentionRemainingCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("구독 행이 없으면 예외가 아니라 BASIC 한도로 응답한다")
    void noSubscriptionFallsBackToBasic() {
        givenUser();
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
        given(frameCountPort.countByUserId(USER_ID)).willReturn(0);

        SubscriptionUsageResponse response = usageService.getUsage(PUBLIC_ID);

        assertThat(response.planTier()).isEqualTo(PlanTier.BASIC);
        assertThat(response.frameRetentionLimit()).isEqualTo(0);
    }

    @Test
    @DisplayName("사용자가 없으면 GEN-031을 던진다")
    void userNotFound() {
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> usageService.getUsage(PUBLIC_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(GlobalErrorCode.NOT_FOUND);
    }

    private void givenUser() {
        User user = User.localUser("user@harucut.com", "encoded", "하루컷");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }

    private void givenTier(PlanTier tier) {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        if (tier != PlanTier.BASIC) {
            subscription.activatePaid(tier,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        }
        givenUser();
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
    }
}
