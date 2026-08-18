package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.subscription.entity.UserSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class GrantActivationService {

    private final UserCouponRepository userCouponRepository;

    public void activate(UserSubscription subscription, LocalDateTime now) {
        UserCoupon userCoupon = userCouponRepository.findById(subscription.getReservedUserCouponId())
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

        subscription.activateGrant(userCoupon.getCoupon().getGrantTier(), now, now.plusMonths(1));
        userCoupon.markRedeemed();
        subscription.clearReservedGrant();
    }
}
