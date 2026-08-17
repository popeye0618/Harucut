package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.subscription.dto.SubscriptionResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.exception.SubscriptionErrorCode;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

    private static final String PUBLIC_ID = "AbCdEf12Gh";
    private static final Long USER_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private SubscriptionService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new SubscriptionService(userRepository, userSubscriptionRepository, fixedClock);
    }

    @Nested
    @DisplayName("getMySubscription")
    class GetMySubscription {

        @Test
        @DisplayName("만료 전 유료 구독은 tier·상태·주기·자동갱신을 그대로 반환한다")
        void activePaidSubscription() {
            LocalDateTime start = LocalDateTime.of(2026, 8, 1, 0, 0);
            LocalDateTime end = LocalDateTime.of(2026, 9, 1, 0, 0);
            givenSubscription(paid(PlanTier.PLUS, start, end));

            SubscriptionResponse response = service.getMySubscription(PUBLIC_ID);

            assertThat(response.planTier()).isEqualTo(PlanTier.PLUS);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.currentPeriodStart()).isEqualTo(start);
            assertThat(response.currentPeriodEnd()).isEqualTo(end);
            assertThat(response.autoRenew()).isTrue();
        }

        @Test
        @DisplayName("공백기 — DB는 PLUS여도 주기가 끝났으면 planTier는 BASIC이다")
        void gapPeriodReturnsEffectiveTier() {
            // 배치가 아직 강등하지 못한 상태: planTier=PLUS, periodEnd는 과거
            givenSubscription(paid(PlanTier.PLUS,
                    LocalDateTime.of(2026, 7, 1, 0, 0),
                    LocalDateTime.of(2026, 8, 1, 0, 0)));

            SubscriptionResponse response = service.getMySubscription(PUBLIC_ID);

            assertThat(response.planTier()).isEqualTo(PlanTier.BASIC);
            assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.currentPeriodEnd()).isEqualTo(LocalDateTime.of(2026, 8, 1, 0, 0));
        }

        @Test
        @DisplayName("BASIC 구독은 주기가 없고 자동갱신도 없다")
        void basicSubscription() {
            givenSubscription(UserSubscription.createBasic(USER_ID));

            SubscriptionResponse response = service.getMySubscription(PUBLIC_ID);

            assertThat(response.planTier()).isEqualTo(PlanTier.BASIC);
            assertThat(response.currentPeriodStart()).isNull();
            assertThat(response.currentPeriodEnd()).isNull();
            assertThat(response.autoRenew()).isFalse();
        }

        @Test
        @DisplayName("구독 행이 없으면 SUBS-004를 던진다")
        void noSubscription() {
            givenUser();
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMySubscription(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION);
        }

        @Test
        @DisplayName("사용자가 없으면 GEN-031을 던진다")
        void userNotFound() {
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMySubscription(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(GlobalErrorCode.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("cancelAutoRenew")
    class CancelAutoRenew {

        @Test
        @DisplayName("ACTIVE 유료 구독은 해지되어 CANCELED, autoRenew=false가 된다")
        void cancelsActivePaid() {
            UserSubscription subscription = paid(PlanTier.PLUS,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
            givenSubscription(subscription);

            service.cancelAutoRenew(PUBLIC_ID);

            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(subscription.isAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("연체(PAST_DUE) 중에도 해지 의사는 유효하다")
        void cancelsPastDue() {
            UserSubscription subscription = paid(PlanTier.PLUS,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
            subscription.markPastDue();
            givenSubscription(subscription);

            service.cancelAutoRenew(PUBLIC_ID);

            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.CANCELED);
            assertThat(subscription.isAutoRenew()).isFalse();
        }

        @Test
        @DisplayName("이미 CANCELED면 SUBS-006이 아니라 SUBS-005다 — 가드 순서 고정")
        void alreadyCanceled() {
            UserSubscription subscription = paid(PlanTier.PLUS,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
            subscription.cancelAutoRenew();
            givenSubscription(subscription);

            assertThatThrownBy(() -> service.cancelAutoRenew(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.ALREADY_CANCELED);
        }

        @Test
        @DisplayName("BASIC은 해지할 자동갱신이 없다 — SUBS-006")
        void basicHasNothingToCancel() {
            givenSubscription(UserSubscription.createBasic(USER_ID));

            assertThatThrownBy(() -> service.cancelAutoRenew(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.NO_AUTO_RENEWAL_TO_CANCEL);
        }

        @Test
        @DisplayName("쿠폰(GRANTED) 구독도 해지할 자동갱신이 없다 — SUBS-006")
        void grantedHasNothingToCancel() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activateGrant(PlanTier.PLUS,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
            givenSubscription(subscription);

            assertThatThrownBy(() -> service.cancelAutoRenew(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.NO_AUTO_RENEWAL_TO_CANCEL);
        }

        @Test
        @DisplayName("구독 행이 없으면 SUBS-004를 던진다")
        void noSubscription() {
            givenUser();
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelAutoRenew(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION);
        }
    }

    private void givenUser() {
        User user = User.localUser("user@harucut.com", "encoded", "하루컷");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }

    private void givenSubscription(UserSubscription subscription) {
        givenUser();
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
    }

    private UserSubscription paid(PlanTier tier, LocalDateTime start, LocalDateTime end) {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(tier, start, end);
        return subscription;
    }
}