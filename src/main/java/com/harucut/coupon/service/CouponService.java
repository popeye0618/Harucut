package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.coupon.dto.MyCouponResponse;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.entity.UserCoupon;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.coupon.repository.UserCouponRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public RedeemResponse redeem(String publicId, String code) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));

        Long userId = user.getId();

        Coupon coupon = couponRepository.findByCode(Coupon.normalizeCode(code))
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now(clock);
        if (!coupon.isRedeemable(now)) {
            throw new BusinessException(CouponErrorCode.COUPON_INACTIVE);
        }

        if (coupon.getMaxRedemptions() != null && coupon.getRedeemedCount() >= coupon.getMaxRedemptions()) {
            throw new BusinessException(CouponErrorCode.COUPON_EXHAUSTED);
        }

        if (userCouponRepository.existsByUserIdAndCouponId(userId, coupon.getId())) {
            throw new BusinessException(CouponErrorCode.COUPON_ALREADY_REDEEMED);
        }

        if (couponRepository.tryIncrementRedeemedCount(coupon.getId()) == 0) {
            throw new BusinessException(CouponErrorCode.COUPON_EXHAUSTED);
        }

        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseGet(() -> userSubscriptionRepository.save(UserSubscription.createBasic(userId)));

        if (subscription.getReservedUserCouponId() != null) {
            throw new BusinessException(CouponErrorCode.RESERVATION_EXISTS);
        }

        if (subscription.effectiveTier(now) == PlanTier.BASIC) {
            return redeemImmediately(coupon, subscription, userId, now);
        }

        if (subscription.getCurrentPeriodEnd() == null) {
            throw new BusinessException(CouponErrorCode.UNLIMITED_SUBSCRIPTION);
        }
        return reserve(coupon, subscription, userId, now);
    }

    @Transactional(readOnly = true)
    public List<MyCouponResponse> getMyCoupons(String publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));

        return userCouponRepository.findAllWithCouponByUserId(user.getId()).stream()
                .map(MyCouponResponse::from)
                .toList();
    }

    private RedeemResponse redeemImmediately(Coupon coupon, UserSubscription subscription, Long userId, LocalDateTime now) {
        LocalDateTime end = now.plusMonths(1);
        subscription.activateGrant(coupon.getGrantTier(), now, end);
        saveUserCoupon(UserCoupon.redeemed(coupon, userId, now));
        return new RedeemResponse(true, coupon.getGrantTier(), now, end);
    }

    private RedeemResponse reserve(Coupon coupon, UserSubscription subscription,
                                   Long userId, LocalDateTime now) {
        UserCoupon userCoupon = saveUserCoupon(UserCoupon.reserved(coupon, userId, now));
        subscription.reserveGrant(userCoupon.getId());

        LocalDateTime start = subscription.getCurrentPeriodEnd();
        return new RedeemResponse(false, coupon.getGrantTier(), start, start.plusMonths(1));
    }

    private UserCoupon saveUserCoupon(UserCoupon userCoupon) {
        try {
            return userCouponRepository.saveAndFlush(userCoupon);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CouponErrorCode.COUPON_ALREADY_REDEEMED);
        }
    }
}
