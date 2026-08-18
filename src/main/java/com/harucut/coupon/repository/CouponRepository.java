package com.harucut.coupon.repository;

import com.harucut.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Coupon c
            SET c.redeemedCount = c.redeemedCount + 1
            WHERE c.id = :couponId
                AND (c.maxRedemptions IS NULL OR c.redeemedCount < c.maxRedemptions)
            """)
    int tryIncrementRedeemedCount(Long couponId);
}
