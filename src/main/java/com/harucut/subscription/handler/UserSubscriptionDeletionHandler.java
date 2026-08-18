package com.harucut.subscription.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserSubscriptionDeletionHandler implements UserDeletionHandler {

    private final UserSubscriptionRepository userSubscriptionRepository;

    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        userSubscriptionRepository.deleteByUserId(userId);
    }
}
