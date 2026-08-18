package com.harucut.payment.repository;

import com.harucut.payment.entity.BillingKey;
import com.harucut.payment.enums.BillingKeyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BillingKeyRepository extends JpaRepository<BillingKey, Long> {

    List<BillingKey> findAllByUserIdAndStatus(Long userId, BillingKeyStatus status);
}
