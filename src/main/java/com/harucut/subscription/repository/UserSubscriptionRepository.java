package com.harucut.subscription.repository;

import com.harucut.subscription.entity.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    boolean existsByUserId(Long userId);

    Optional<UserSubscription> findByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserSubscription s WHERE s.userId = :userId")
    void deleteByUserId(Long userId);
}