package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.exception.SubscriptionErrorCode;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SubscriptionPolicyServiceTest {

    private static final Long USER_ID = 1L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 16, 12, 0);

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    private SubscriptionPolicyService policyService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        policyService = new SubscriptionPolicyService(userSubscriptionRepository, fixedClock);
    }

    @Nested
    @DisplayName("assertFrameRetentionLimit")
    class AssertFrameRetentionLimit {

        @Test
        @DisplayName("BASIC은 0개 보유 상태에서도 생성이 거부된다 — SUBS-003")
        void basicCannotCreateAtAll() {
            givenTier(PlanTier.BASIC);

            assertThatThrownBy(() -> policyService.assertFrameRetentionLimit(USER_ID, 0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.PLAN_FRAME_RETENTION_EXCEEDED);
        }

        @Test
        @DisplayName("PLUS는 2개 보유 상태에서 3번째 생성이 허용된다")
        void plusAllowsThirdFrame() {
            givenTier(PlanTier.PLUS);

            assertThatCode(() -> policyService.assertFrameRetentionLimit(USER_ID, 2))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("PLUS는 3개 보유 상태에서 4번째 생성이 거부된다 — 경계")
        void plusRejectsFourthFrame() {
            givenTier(PlanTier.PLUS);

            assertThatThrownBy(() -> policyService.assertFrameRetentionLimit(USER_ID, 3))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.PLAN_FRAME_RETENTION_EXCEEDED);
        }

        @Test
        @DisplayName("PRO는 몇 개를 보유해도 허용된다")
        void proAlwaysAllows() {
            givenTier(PlanTier.PRO);

            assertThatCode(() -> policyService.assertFrameRetentionLimit(USER_ID, Integer.MAX_VALUE))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("resolveFrameRetentionCap")
    class ResolveFrameRetentionCap {

        @Test
        @DisplayName("BASIC은 0, PLUS는 3이다")
        void limitedTiersReturnCap() {
            givenTier(PlanTier.BASIC);
            assertThat(policyService.resolveFrameRetentionCap(USER_ID)).isEqualTo(0);

            givenTier(PlanTier.PLUS);
            assertThat(policyService.resolveFrameRetentionCap(USER_ID)).isEqualTo(3);
        }

        @Test
        @DisplayName("PRO는 무제한이라 null이다")
        void unlimitedReturnsNull() {
            givenTier(PlanTier.PRO);

            assertThat(policyService.resolveFrameRetentionCap(USER_ID)).isNull();
        }
    }


    @Nested
    @DisplayName("resolveHistoryCutoff")
    class ResolveHistoryCutoff {

        @Test
        @DisplayName("BASIC의 cutoff는 3일 전이다")
        void basicCutoff() {
            givenTier(PlanTier.BASIC);

            assertThat(policyService.resolveHistoryCutoff(USER_ID))
                    .isEqualTo(LocalDateTime.of(2026, 8, 13, 12, 0));
        }

        @Test
        @DisplayName("PLUS의 cutoff는 달력 기준 3개월 전이다")
        void plusCutoff() {
            givenTier(PlanTier.PLUS);

            assertThat(policyService.resolveHistoryCutoff(USER_ID))
                    .isEqualTo(LocalDateTime.of(2026, 5, 16, 12, 0));
        }

        @Test
        @DisplayName("PRO는 cutoff가 없다 — null")
        void proCutoff() {
            givenTier(PlanTier.PRO);

            assertThat(policyService.resolveHistoryCutoff(USER_ID)).isNull();
        }
    }

    @Nested
    @DisplayName("assertHistoryAccessible")
    class AssertHistoryAccessible {

        @Test
        @DisplayName("정확히 cutoff 시각에 만든 내역은 접근할 수 있다 — 경계 포함")
        void exactlyAtCutoffIsAccessible() {
            givenTier(PlanTier.PLUS);

            assertThatCode(() -> policyService.assertHistoryAccessible(
                    USER_ID, LocalDateTime.of(2026, 5, 16, 12, 0)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("cutoff보다 오래된 내역은 SUBS-002다")
        void olderThanCutoffIsRejected() {
            givenTier(PlanTier.PLUS);

            assertThatThrownBy(() -> policyService.assertHistoryAccessible(
                    USER_ID, LocalDateTime.of(2026, 5, 16, 11, 59, 59)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(SubscriptionErrorCode.PLAN_HISTORY_RETENTION_EXCEEDED);
        }

        @Test
        @DisplayName("PRO는 아무리 오래된 내역도 접근할 수 있다")
        void proAccessesAnything() {
            givenTier(PlanTier.PRO);

            assertThatCode(() -> policyService.assertHistoryAccessible(
                    USER_ID, NOW.minusYears(10)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("생성 시각을 모르는 내역은 막지 않는다")
        void unknownCreatedAtIsAccessible() {
            givenTier(PlanTier.BASIC);

            assertThatCode(() -> policyService.assertHistoryAccessible(USER_ID, null))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("구독이 없거나 만료된 경우")
    class Fallback {

        @Test
        @DisplayName("구독 행이 없으면 예외가 아니라 BASIC 정책으로 판정한다")
        void noSubscriptionFallsBackToBasic() {
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThat(policyService.resolveHistoryCutoff(USER_ID))
                    .isEqualTo(LocalDateTime.of(2026, 8, 13, 12, 0));
            assertThatThrownBy(() -> policyService.assertFrameRetentionLimit(USER_ID, 0))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("공백기 — 주기가 끝난 PLUS는 배치 전이라도 BASIC 정책으로 판정한다")
        void expiredPlusIsJudgedAsBasic() {
            UserSubscription subscription = UserSubscription.createBasic(USER_ID);
            subscription.activatePaid(PlanTier.PLUS,
                    LocalDateTime.of(2026, 7, 1, 0, 0), LocalDateTime.of(2026, 8, 1, 0, 0));
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));

            // PLUS였다면 5/16이 나와야 하지만, effectiveTier가 BASIC으로 떨어져 3일 전이 나온다
            assertThat(policyService.resolveHistoryCutoff(USER_ID))
                    .isEqualTo(LocalDateTime.of(2026, 8, 13, 12, 0));
        }
    }

    private void givenTier(PlanTier tier) {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        if (tier != PlanTier.BASIC) {
            subscription.activatePaid(tier,
                    LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0));
        }
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
    }
}