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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final Clock clock;

    public SubscriptionResponse getMySubscription(String publicId) {
        UserSubscription subscription = getSubscription(publicId);
        PlanTier effectiveTier = subscription.effectiveTier(LocalDateTime.now(clock));
        return SubscriptionResponse.of(subscription, effectiveTier);
    }

    @Transactional
    public void cancelAutoRenew(String publicId) {
        UserSubscription subscription = getSubscription(publicId);

        if (subscription.getSubscriptionStatus() == SubscriptionStatus.CANCELED) {
            throw new BusinessException(SubscriptionErrorCode.ALREADY_CANCELED);
        }
        if (!subscription.isAutoRenew()) {
            throw new BusinessException(SubscriptionErrorCode.NO_AUTO_RENEWAL_TO_CANCEL);
        }

        subscription.cancelAutoRenew();
    }

    private UserSubscription getSubscription(String publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));
        return userSubscriptionRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION));
    }
}
