package com.harucut.subscription.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.subscription.dto.SubscriptionUsageResponse;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.subscription.port.FrameCountPort;
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
public class SubscriptionUsageService {

    private final UserRepository userRepository;
    private final UserSubscriptionRepository userSubscriptionRepository;
    private final FrameCountPort frameCountPort;
    private final Clock clock;

    public SubscriptionUsageResponse getUsage(String publicId) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));

        PlanTier effectiveTier = userSubscriptionRepository.findByUserId(user.getId())
                .map(subscription -> subscription.effectiveTier(LocalDateTime.now(clock)))
                .orElse(PlanTier.BASIC);

        int usedCount = frameCountPort.countByUserId(user.getId());

        return SubscriptionUsageResponse.of(effectiveTier, usedCount);
    }
}
