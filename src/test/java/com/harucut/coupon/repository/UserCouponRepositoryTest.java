package com.harucut.coupon.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @AutoConfigureJson: frame 엔티티의 AttributeConverter가 앱 ObjectMapper를 주입받는데,
// Hibernate는 어떤 엔티티를 쓰든 메타모델 전체를 만들므로 모든 @DataJpaTest에 Jackson이 필요하다
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
class UserCouponRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Autowired
    private CouponRepository couponRepository;

    /*
     * 중복 사용의 최후 방어선.
     * 서비스의 exists 검사가 경쟁 요청에 뚫려도 두 번째 INSERT는 여기서 막힌다.
     */
    @Test
    @DisplayName("같은 사용자가 같은 쿠폰을 두 번 저장하면 DB 제약에 걸린다")
    void rejectsDuplicateRedemption() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1"));
        userCouponRepository.saveAndFlush(UserCoupon.redeemed(coupon, 1L, NOW));

        assertThatThrownBy(() ->
                userCouponRepository.saveAndFlush(UserCoupon.redeemed(coupon, 1L, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("다른 사용자는 같은 쿠폰을 쓸 수 있다")
    void allowsDifferentUsers() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1"));
        userCouponRepository.save(UserCoupon.redeemed(coupon, 1L, NOW));
        userCouponRepository.save(UserCoupon.redeemed(coupon, 2L, NOW));
        userCouponRepository.flush();

        assertThat(userCouponRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("한 사용자가 다른 쿠폰을 쓰는 건 막지 않는다")
    void allowsDifferentCoupons() {
        Coupon first = couponRepository.saveAndFlush(coupon("CODE-1"));
        Coupon second = couponRepository.saveAndFlush(coupon("CODE-2"));
        userCouponRepository.save(UserCoupon.redeemed(first, 1L, NOW));
        userCouponRepository.save(UserCoupon.reserved(second, 1L, NOW));
        userCouponRepository.flush();

        assertThat(userCouponRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("existsByUserIdAndCouponId는 그 사용자·그 쿠폰 조합만 참이다")
    void existsByUserIdAndCouponId() {
        Coupon coupon = couponRepository.saveAndFlush(coupon("CODE-1"));
        userCouponRepository.saveAndFlush(UserCoupon.redeemed(coupon, 1L, NOW));

        assertThat(userCouponRepository.existsByUserIdAndCouponId(1L, coupon.getId())).isTrue();
        assertThat(userCouponRepository.existsByUserIdAndCouponId(2L, coupon.getId())).isFalse();
    }

    private Coupon coupon(String code) {
        return Coupon.create("가입 축하 PRO", code, PlanTier.PRO, null, null);
    }
}
