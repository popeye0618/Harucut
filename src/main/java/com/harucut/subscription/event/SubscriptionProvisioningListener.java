package com.harucut.subscription.event;

import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.user.event.UserRegisterEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionProvisioningListener {

    private final UserSubscriptionRepository userSubscriptionRepository;

    @EventListener
    public void handleUserRegistered(UserRegisterEvent event) {
        if (userSubscriptionRepository.existsByUserId(event.userId())) {
            return;
        }

        userSubscriptionRepository.save(UserSubscription.createBasic(event.userId()));
    }
}
