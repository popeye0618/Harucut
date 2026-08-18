package com.harucut.subscription.service;

import com.harucut.coupon.service.GrantActivationService;
import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.repository.BillingKeyRepository;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionExpirationService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final BillingKeyRepository billingKeyRepository;
    private final GrantActivationService grantActivationService;

    @Transactional
    public void expire(Long subscriptionId, LocalDateTime baseTime) {
        UserSubscription subscription = userSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            return;
        }

        if (subscription.getReservedUserCouponId() != null) {
            grantActivationService.activate(subscription, baseTime);
            return;
        }

        subscription.expireToFree();
        billingKeyRepository.findAllByUserIdAndStatus(subscription.getUserId(), BillingKeyStatus.ACTIVE)
                .forEach(BillingKey::delete);
    }
}
