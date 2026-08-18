package com.harucut.subscription.handler;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.subscription.entity.UserSubscription;
import com.harucut.subscription.repository.UserSubscriptionRepository;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class, UserSubscriptionDeletionHandler.class})
@ActiveProfiles("test")
@DisplayName("UserSubscriptionDeletionHandler")
class UserSubscriptionDeletionHandlerTest {

    @Autowired
    private UserSubscriptionDeletionHandler handler;

    @Autowired
    private UserSubscriptionRepository userSubscriptionRepository;

    @Test
    @DisplayName("내 구독만 지워지고 다른 사용자 구독은 남는다")
    void deletesOnlyMine() {
        userSubscriptionRepository.save(UserSubscription.createBasic(1L));
        userSubscriptionRepository.save(UserSubscription.createBasic(2L));
        userSubscriptionRepository.flush();

        handler.handleUserDeletion(1L);

        assertThat(userSubscriptionRepository.findByUserId(1L)).isEmpty();
        assertThat(userSubscriptionRepository.findByUserId(2L)).isPresent();
    }
}
