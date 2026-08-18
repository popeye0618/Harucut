package com.harucut.coupon.handler;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

// 핸들러가 얇아서(쿼리 한 줄) 진짜 DB로 핸들러째 검증한다 — 이게 곧 벌크 쿼리 테스트다
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class, CouponUserDeletionHandler.class})
@ActiveProfiles("test")
@DisplayName("CouponUserDeletionHandler")
class CouponUserDeletionHandlerTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 18, 10, 0);

    @Autowired
    private CouponUserDeletionHandler handler;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private UserCouponRepository userCouponRepository;

    @Test
    @DisplayName("내 사용 이력만 지워지고 다른 사용자 것은 남는다")
    void deletesOnlyMine() {
        Coupon coupon = couponRepository.save(Coupon.create("가입 축하", "CODE-1", PlanTier.PRO, null, null));
        userCouponRepository.save(UserCoupon.redeemed(coupon, 1L, NOW));
        userCouponRepository.save(UserCoupon.redeemed(coupon, 2L, NOW));
        userCouponRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(userCouponRepository.findAll()).singleElement()
                .satisfies(row -> assertThat(row.getUserId()).isEqualTo(2L));
    }
}
