package com.harucut.payment.repository;

import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.enums.BillingKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    List<BillingKey> findAllByUserIdAndStatus(Long userId, BillingKeyStatus status);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM BillingKey b WHERE b.userId = :userId")
    void deleteByUserId(Long userId);
}
