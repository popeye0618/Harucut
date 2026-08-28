package com.harucut.subscription.service;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
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

// 요금제 한도 임시 통일(전부 무제한) 상태의 기대값이다.
// 정책이 다시 갈라지면 git 이력의 이전 버전이 요금제별 시나리오 목록이다 —
// SUBS-003(보관 한도 초과)·SUBS-002(내역 기간 초과) throw 경로는 지금 어떤 요금제로도
// 도달할 수 없어 커버리지를 잃은 상태고, 그때 반드시 되살려야 한다
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
        @DisplayName("모든 요금제가 몇 개를 보유해도 허용된다")
        void allTiersAlwaysAllow() {
            for (PlanTier tier : PlanTier.values()) {
                givenTier(tier);

                assertThatCode(() ->
                        policyService.assertFrameRetentionLimit(USER_ID, Integer.MAX_VALUE))
                        .doesNotThrowAnyException();
            }
        }
    }

    @Nested
    @DisplayName("resolveFrameRetentionCap")
    class ResolveFrameRetentionCap {

        @Test
        @DisplayName("모든 요금제가 무제한이라 null이다")
        void allTiersReturnNull() {
            for (PlanTier tier : PlanTier.values()) {
                givenTier(tier);

                assertThat(policyService.resolveFrameRetentionCap(USER_ID)).isNull();
            }
        }
    }

    @Nested
    @DisplayName("resolveHistoryCutoff")
    class ResolveHistoryCutoff {

        @Test
        @DisplayName("모든 요금제가 cutoff 없음 — null")
        void allTiersReturnNull() {
            for (PlanTier tier : PlanTier.values()) {
                givenTier(tier);

                assertThat(policyService.resolveHistoryCutoff(USER_ID)).isNull();
            }
        }
    }

    @Nested
    @DisplayName("assertHistoryAccessible")
    class AssertHistoryAccessible {

        @Test
        @DisplayName("아무리 오래된 내역도 모든 요금제에서 접근할 수 있다")
        void anyTierAccessesAnything() {
            for (PlanTier tier : PlanTier.values()) {
                givenTier(tier);

                assertThatCode(() -> policyService.assertHistoryAccessible(
                        USER_ID, NOW.minusYears(10)))
                        .doesNotThrowAnyException();
            }
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
    @DisplayName("구독이 없는 경우")
    class Fallback {

        // 만료된 PLUS → BASIC 강등(effectiveTier)은 정책이 전부 같아진 지금 여기서는
        // 관측할 수 없다 — UserSubscriptionTest가 tier 판정 자체를 커버한다
        @Test
        @DisplayName("구독 행이 없으면 예외가 아니라 BASIC 정책으로 판정한다")
        void noSubscriptionFallsBackToBasic() {
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThat(policyService.resolveHistoryCutoff(USER_ID)).isNull();
            assertThatCode(() -> policyService.assertFrameRetentionLimit(USER_ID, 0))
                    .doesNotThrowAnyException();
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
