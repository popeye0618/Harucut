package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.subscription.dto.SubscriptionAdminResponse;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.exception.SubscriptionErrorCode;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionAdminService {

    private final UserSubscriptionRepository userSubscriptionRepository;
    private final Clock clock;

    public SubscriptionAdminResponse getSubscription(Long userId) {
        UserSubscription subscription = userSubscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(SubscriptionErrorCode.NO_ACTIVE_SUBSCRIPTION));
        PlanTier effectiveTier = subscription.effectiveTier(LocalDateTime.now(clock));
        return SubscriptionAdminResponse.of(subscription, effectiveTier);
    }
}