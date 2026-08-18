package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.coupon.dto.MyCouponResponse;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService")
class CouponServiceTest {

    private static final String PUBLIC_ID = "user-pub-001";
    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 5L;
    private static final Long USER_COUPON_ID = 42L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    @Mock
    private UserSubscriptionRepository userSubscriptionRepository;

    @Mock
    private UserRepository userRepository;

    private CouponService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new CouponService(couponRepository, userCouponRepository,
                userSubscriptionRepository, userRepository, clock);
    }

    @Nested
    @DisplayName("검증")
    class Validation {

        @Test
        @DisplayName("없는 사용자는 404이고 쿠폰 조회까지 가지 않는다")
        void unknownUser() {
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(GlobalErrorCode.NOT_FOUND);

            then(couponRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("없는 코드는 COUPON-001이다")
        void unknownCode() {
            givenUser();
            given(couponRepository.findByCode("NO-SUCH-CODE")).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "NO-SUCH-CODE"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_NOT_FOUND);
        }

        // 소문자 조회가 빈손인 건 리포지토리 테스트에서 증명했다 — 여기서는 서비스가 대문자로 바꿔 조회함을 증명한다
        @Test
        @DisplayName("소문자로 입력해도 대문자로 정규화해서 조회한다")
        void normalizesCodeBeforeLookup() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            givenBasicSubscription();
            givenSaveReturnsWithId();

            service.redeem(PUBLIC_ID, "  welcome-pro  ");

            then(couponRepository).should().findByCode("WELCOME-PRO");
        }

        @Test
        @DisplayName("비활성 쿠폰은 COUPON-004이고 관문(+1)까지 가지 않는다")
        void inactiveCoupon() {
            givenUser();
            Coupon coupon = coupon();
            coupon.deactivate();
            givenCoupon(coupon);

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_INACTIVE);

            then(couponRepository).should(never()).tryIncrementRedeemedCount(anyLong());
        }

        @Test
        @DisplayName("마감이 지난 쿠폰은 COUPON-004다")
        void expiredCoupon() {
            givenUser();
            givenCoupon(coupon(100, NOW.minusSeconds(1)));

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_INACTIVE);
        }

        @Test
        @DisplayName("문앞 상한 검사에 걸리면 COUPON-005이고 관문(+1)을 부르지 않는다")
        void exhaustedAtDoorway() {
            givenUser();
            Coupon coupon = coupon(100, null);
            ReflectionTestUtils.setField(coupon, "redeemedCount", 100);
            givenCoupon(coupon);

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_EXHAUSTED);

            then(couponRepository).should(never()).tryIncrementRedeemedCount(anyLong());
        }

        @Test
        @DisplayName("이미 쓴 쿠폰은 COUPON-006이고 관문(+1)을 부르지 않는다")
        void alreadyRedeemed() {
            givenUser();
            givenCoupon(coupon());
            given(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).willReturn(true);

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_ALREADY_REDEEMED);

            then(couponRepository).should(never()).tryIncrementRedeemedCount(anyLong());
        }

        /*
         * 문앞 검사(카운터 읽기)는 통과했는데 관문(+1)이 0을 반환하는 상황 —
         * 그 짧은 사이에 다른 요청들이 마지막 장을 가져간 경우다. 관문이 최후 판정자다.
         */
        @Test
        @DisplayName("관문(+1)이 0을 반환하면 COUPON-005이고 구독은 건드리지 않는다")
        void exhaustedAtGate() {
            givenUser();
            givenCoupon(coupon());
            given(couponRepository.tryIncrementRedeemedCount(COUPON_ID)).willReturn(0);

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_EXHAUSTED);

            then(userSubscriptionRepository).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("이미 예약이 있으면 COUPON-007이고 이력을 저장하지 않는다")
        void reservationAlreadyExists() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            UserSubscription subscription = paidSubscription(NOW.plusDays(10));
            subscription.reserveGrant(7L);
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.RESERVATION_EXISTS);

            then(userCouponRepository).should(never()).saveAndFlush(any(UserCoupon.class));
        }

        @Test
        @DisplayName("무기한 유료 구독은 COUPON-008이고 이력을 저장하지 않는다")
        void unlimitedPaidSubscriptionRejected() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            given(userSubscriptionRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(paidSubscription(null)));

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.UNLIMITED_SUBSCRIPTION);

            then(userCouponRepository).should(never()).saveAndFlush(any(UserCoupon.class));
        }
    }

    @Nested
    @DisplayName("즉시 개시")
    class ImmediateRedemption {

        @Test
        @DisplayName("BASIC 사용자는 applied=true이고 주기는 now부터 한 달이다")
        void basicUserGetsImmediateGrant() {
            givenHappyPathForBasic();

            RedeemResponse response = service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(response.applied()).isTrue();
            assertThat(response.grantTier()).isEqualTo(PlanTier.PRO);
            assertThat(response.startsAt()).isEqualTo(NOW);
            assertThat(response.endsAt()).isEqualTo(NOW.plusMonths(1));
        }

        @Test
        @DisplayName("구독이 쿠폰 tier의 GRANTED로 바뀐다")
        void subscriptionBecomesGranted() {
            UserSubscription subscription = givenHappyPathForBasic();

            service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PRO);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRANTED);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(NOW.plusMonths(1));
        }

        @Test
        @DisplayName("REDEEMED 상태의 사용 이력이 저장된다")
        void savesRedeemedHistory() {
            givenHappyPathForBasic();

            service.redeem(PUBLIC_ID, "WELCOME-PRO");

            ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
            then(userCouponRepository).should().saveAndFlush(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(UserCouponStatus.REDEEMED);
            assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
            assertThat(captor.getValue().getRedeemedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("구독이 없으면 BASIC 구독을 만들어 즉시 개시한다")
        void createsSubscriptionWhenMissing() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.empty());
            given(userSubscriptionRepository.save(any(UserSubscription.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            givenSaveReturnsWithId();

            RedeemResponse response = service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(response.applied()).isTrue();
            then(userSubscriptionRepository).should().save(any(UserSubscription.class));
        }

        // effectiveTier가 판정 기준임을 증명 — DB의 planTier는 아직 유료라도 기간이 끝났으면 BASIC이다
        @Test
        @DisplayName("유료였지만 기간이 끝난 사용자는 예약이 아니라 즉시 개시다")
        void lapsedPaidUserGetsImmediateGrant() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            given(userSubscriptionRepository.findByUserId(USER_ID))
                    .willReturn(Optional.of(paidSubscription(NOW.minusDays(1))));
            givenSaveReturnsWithId();

            RedeemResponse response = service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(response.applied()).isTrue();
            assertThat(response.startsAt()).isEqualTo(NOW);
        }

        /*
         * 동시 중복 요청이 exists 검사를 나란히 통과한 경우 —
         * 두 번째 INSERT는 (user_id, coupon_id) unique에 걸리고, 500이 아니라 COUPON-006으로 나간다.
         */
        @Test
        @DisplayName("unique 제약에 걸린 동시 중복 사용은 COUPON-006으로 변환된다")
        void translatesConstraintViolationToAlreadyRedeemed() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            givenBasicSubscription();
            given(userCouponRepository.saveAndFlush(any(UserCoupon.class)))
                    .willThrow(new DataIntegrityViolationException("duplicate"));

            assertThatThrownBy(() -> service.redeem(PUBLIC_ID, "WELCOME-PRO"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(CouponErrorCode.COUPON_ALREADY_REDEEMED);
        }

        private UserSubscription givenHappyPathForBasic() {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            UserSubscription subscription = givenBasicSubscription();
            givenSaveReturnsWithId();
            return subscription;
        }
    }

    @Nested
    @DisplayName("예약")
    class Reservation {

        @Test
        @DisplayName("유료 구독 중이면 applied=false이고 시작은 현재 주기 종료 시각이다")
        void paidUserGetsReservation() {
            LocalDateTime periodEnd = NOW.plusDays(10);
            givenHappyPathForPaid(periodEnd);

            RedeemResponse response = service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(response.applied()).isFalse();
            assertThat(response.startsAt()).isEqualTo(periodEnd);
            assertThat(response.endsAt()).isEqualTo(periodEnd.plusMonths(1));
        }

        @Test
        @DisplayName("RESERVED 상태의 사용 이력이 저장된다")
        void savesReservedHistory() {
            givenHappyPathForPaid(NOW.plusDays(10));

            service.redeem(PUBLIC_ID, "WELCOME-PRO");

            ArgumentCaptor<UserCoupon> captor = ArgumentCaptor.forClass(UserCoupon.class);
            then(userCouponRepository).should().saveAndFlush(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(UserCouponStatus.RESERVED);
        }

        @Test
        @DisplayName("구독에 저장된 이력의 PK가 예약으로 기록된다")
        void recordsReservationOnSubscription() {
            UserSubscription subscription = givenHappyPathForPaid(NOW.plusDays(10));

            service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(subscription.getReservedUserCouponId()).isEqualTo(USER_COUPON_ID);
        }

        @Test
        @DisplayName("구독 자체는 tier도 주기도 바뀌지 않는다 — 돈 낸 구독을 덮지 않는다")
        void subscriptionStaysUntouched() {
            LocalDateTime periodEnd = NOW.plusDays(10);
            UserSubscription subscription = givenHappyPathForPaid(periodEnd);

            service.redeem(PUBLIC_ID, "WELCOME-PRO");

            assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);
            assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(periodEnd);
        }

        private UserSubscription givenHappyPathForPaid(LocalDateTime periodEnd) {
            givenUser();
            givenCoupon(coupon());
            givenGatePasses();
            UserSubscription subscription = paidSubscription(periodEnd);
            given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
            givenSaveReturnsWithId();
            return subscription;
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록")
    class MyCoupons {

        @Test
        @DisplayName("없는 사용자는 404다")
        void unknownUser() {
            given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.getMyCoupons(PUBLIC_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode").isEqualTo(GlobalErrorCode.NOT_FOUND);
        }

        // publicId는 쿠폰이 아니라 사용 이력의 것이다 — 명세가 강조하는 부분
        @Test
        @DisplayName("이력이 응답으로 매핑되고 publicId는 이력의 것이다")
        void mapsHistoryToResponse() {
            givenUser();
            UserCoupon userCoupon = UserCoupon.redeemed(coupon(), USER_ID, NOW);
            given(userCouponRepository.findAllWithCouponByUserId(USER_ID))
                    .willReturn(List.of(userCoupon));

            List<MyCouponResponse> result = service.getMyCoupons(PUBLIC_ID);

            assertThat(result).singleElement().satisfies(response -> {
                assertThat(response.publicId()).isEqualTo(userCoupon.getPublicId());
                assertThat(response.couponName()).isEqualTo("가입 축하 PRO");
                assertThat(response.grantTier()).isEqualTo(PlanTier.PRO);
                assertThat(response.status()).isEqualTo(UserCouponStatus.REDEEMED);
                assertThat(response.redeemedAt()).isEqualTo(NOW);
            });
        }

        @Test
        @DisplayName("이력이 없으면 빈 목록이다")
        void emptyHistory() {
            givenUser();
            given(userCouponRepository.findAllWithCouponByUserId(USER_ID)).willReturn(List.of());

            assertThat(service.getMyCoupons(PUBLIC_ID)).isEmpty();
        }
    }

    private void givenUser() {
        User user = UserFixtures.localUser("coupon@harucut.com", "encoded");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }

    private Coupon coupon() {
        return coupon(100, null);
    }

    private Coupon coupon(Integer maxRedemptions, LocalDateTime validUntil) {
        Coupon coupon = Coupon.create("가입 축하 PRO", "WELCOME-PRO", PlanTier.PRO, maxRedemptions, validUntil);
        ReflectionTestUtils.setField(coupon, "id", COUPON_ID);
        return coupon;
    }

    private void givenCoupon(Coupon coupon) {
        given(couponRepository.findByCode("WELCOME-PRO")).willReturn(Optional.of(coupon));
    }

    private void givenGatePasses() {
        given(couponRepository.tryIncrementRedeemedCount(COUPON_ID)).willReturn(1);
    }

    private UserSubscription givenBasicSubscription() {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        given(userSubscriptionRepository.findByUserId(USER_ID)).willReturn(Optional.of(subscription));
        return subscription;
    }

    private UserSubscription paidSubscription(LocalDateTime periodEnd) {
        UserSubscription subscription = UserSubscription.createBasic(USER_ID);
        subscription.activatePaid(PlanTier.PLUS, NOW.minusDays(20), periodEnd);
        return subscription;
    }

    private void givenSaveReturnsWithId() {
        given(userCouponRepository.saveAndFlush(any(UserCoupon.class))).willAnswer(invocation -> {
            UserCoupon saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", USER_COUPON_ID);
            return saved;
        });
    }
}
