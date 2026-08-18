package com.harucut.coupon;

import com.harucut.common.exception.BusinessException;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.enums.UserCouponStatus;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.coupon.service.CouponService;
import com.harucut.coupon.service.GrantActivationService;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.enums.SubscriptionStatus;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("쿠폰 사용 통합")
public class CouponRedeemFlowTest {

    private static final LocalDateTime PERIOD_END = LocalDateTime.of(2027, 1, 1, 0, 0);

    @Autowired
    private CouponService couponService;

    @Autowired
    private GrantActivationService grantActivationService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("BASIC 사용자는 즉시 개시되어 구독이 GRANTED가 되고 기록이 남는다")
    void basicUserGetsGrantImmediately() {
        User user = newBasicUser("coupon-now@harucut.com");
        Coupon coupon = newCoupon("IT-NOW-1", PlanTier.PRO, null);

        RedeemResponse response = couponService.redeem(user.getPublicId(), "IT-NOW-1");

        assertThat(response.applied()).isTrue();
        assertThat(response.endsAt()).isEqualTo(response.startsAt().plusMonths(1));

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PRO);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRANTED);

        assertThat(rowsOf(coupon)).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(UserCouponStatus.REDEEMED));
        assertThat(redeemedCountOf("IT-NOW-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("유료 구독 중이면 구독은 그대로이고 예약만 기록된다")
    void paidUserGetsReservation() {
        User user = newPaidUser("coupon-later@harucut.com");
        Coupon coupon = newCoupon("IT-LATER-1", PlanTier.PLUS, null);

        RedeemResponse response = couponService.redeem(user.getPublicId(), "IT-LATER-1");

        assertThat(response.applied()).isFalse();
        assertThat(response.startsAt()).isEqualTo(PERIOD_END);

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PRO);
        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END);

        UserCoupon reserved = rowsOf(coupon).get(0);
        assertThat(reserved.getStatus()).isEqualTo(UserCouponStatus.RESERVED);
        assertThat(subscription.getReservedUserCouponId()).isEqualTo(reserved.getId());
    }

    @Test
    @DisplayName("예약된 쿠폰은 활성화 시점에 구독을 넘겨받고 이력이 REDEEMED가 된다")
    void reservedCouponActivatesLater() {
        User user = newPaidUser("coupon-cycle@harucut.com");
        newCoupon("IT-CYCLE-1", PlanTier.PLUS, null);
        couponService.redeem(user.getPublicId(), "IT-CYCLE-1");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId())
                    .orElseThrow();
            grantActivationService.activate(subscription, PERIOD_END);
        });

        UserSubscription subscription = userSubscriptionRepository.findByUserId(user.getId()).orElseThrow();
        assertThat(subscription.getPlanTier()).isEqualTo(PlanTier.PLUS);

        assertThat(subscription.getSubscriptionStatus()).isEqualTo(SubscriptionStatus.GRANTED);
        assertThat(subscription.getCurrentPeriodEnd()).isEqualTo(PERIOD_END.plusMonths(1));
        assertThat(subscription.getReservedUserCouponId()).isNull();

        Coupon coupon = couponRepository.findByCode("IT-CYCLE-1").orElseThrow();
        assertThat(rowsOf(coupon)).singleElement()
                .satisfies(row -> assertThat(row.getStatus()).isEqualTo(UserCouponStatus.REDEEMED));
    }

    /*
     * 상한 1짜리 쿠폰에 10명이 동시에 돌진한다.
     * "세고 → 저장"이었다면 성공이 2건 이상 나올 수 있다.
     * 조건부 UPDATE 관문이 정확히 1건만 통과시킴을 증명한다.
     */
    @Test
    @DisplayName("상한 1인 쿠폰에 10명이 동시 요청하면 성공은 정확히 1건이다.")
    void capHoldsUnderConcurrency() throws InterruptedException {
        List<User> users = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> newBasicUser("race-" + i + "@harucut.com"))
                .toList();
        newCoupon("IT-RACE-1", PlanTier.PRO, 1);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger exhausted = new AtomicInteger();

        runConcurrently(users, publicId -> {
            try {
                couponService.redeem(publicId, "IT-RACE-1");
                success.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == CouponErrorCode.COUPON_EXHAUSTED) {
                    exhausted.incrementAndGet();
                }
            }
        });
        assertThat(success.get()).isEqualTo(1);
        assertThat(exhausted.get()).isEqualTo(9);
        assertThat(redeemedCountOf("IT-RACE-1")).isEqualTo(1);
        assertThat(rowsOf(couponRepository.findByCode("IT-RACE-1").orElseThrow())).hasSize(1);
    }

    /*
     * 같은 사용자의 동시 중복 — 둘 다 exists 검사를 통과해도
     * 두 번째 INSERT는 unique 제약에서 죽고, 그쪽이 올렸던 +1도 롤백으로 되돌아간다.
     * 최종적으로 이력 1건, 카운터 1 — 둘이 어긋날 수 없다는 증명이다.
     */
    @Test
    @DisplayName("같은 사용자가 동시에 두 번 써도 이력은 1건이고 카운터도 1이다")
    void duplicateRedemptionUnderConcurrency() throws InterruptedException {
        User user = newBasicUser("dup-race@harucut.com");
        newCoupon("IT-DUP-1", PlanTier.PRO, null);

        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicated = new AtomicInteger();
        runConcurrently(List.of(user, user), publicId -> {
            try {
                couponService.redeem(publicId, "IT-DUP-1");
                success.incrementAndGet();
            } catch (BusinessException e) {
                if (e.getErrorCode() == CouponErrorCode.COUPON_ALREADY_REDEEMED) {
                    duplicated.incrementAndGet();
                }
            }
        });

        assertThat(success.get()).isEqualTo(1);
        assertThat(duplicated.get()).isEqualTo(1);
        assertThat(rowsOf(couponRepository.findByCode("IT-DUP-1").orElseThrow())).hasSize(1);
        assertThat(redeemedCountOf("IT-DUP-1")).isEqualTo(1);
    }

    private void runConcurrently(List<User> users, Consumer<String> task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(users.size());
        CountDownLatch ready = new CountDownLatch(users.size());
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(users.size());

        for (User user : users) {
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    task.accept(user.getPublicId());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
    }

    private User newBasicUser(String email) {
        User user = userRepository.save(UserFixtures.localUser(email, "encoded"));
        userSubscriptionRepository.save(UserSubscription.createBasic(user.getId()));
        return user;
    }

    private User newPaidUser(String email) {
        User user = userRepository.save(UserFixtures.localUser(email, "encoded"));
        UserSubscription subscription = UserSubscription.createBasic(user.getId());
        subscription.activatePaid(PlanTier.PRO, PERIOD_END.minusMonths(1), PERIOD_END);
        userSubscriptionRepository.save(subscription);
        return user;
    }

    private Coupon newCoupon(String code, PlanTier tier, Integer maxRedemptions) {
        return couponRepository.save(Coupon.create("통합 테스트 쿠폰", code, tier, maxRedemptions, null));
    }

    private int redeemedCountOf(String code) {
        return couponRepository.findByCode(code).orElseThrow().getRedeemedCount();
    }

    private List<UserCoupon> rowsOf(Coupon coupon) {
        return userCouponRepository.findAll().stream()
                .filter(row -> row.getCoupon().getId().equals(coupon.getId()))
                .toList();
    }
}
