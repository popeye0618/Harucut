package com.harucut.coupon.repository;

import com.harucut.coupon.entity.UserCoupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {

    boolean existsByUserIdAndCouponId(Long userId, Long couponId);

    @Query("""
        SELECT uc FROM UserCoupon uc
        JOIN FETCH uc.coupon
        WHERE uc.userId = :userId
        ORDER BY uc.id DESC
        """)
    List<UserCoupon> findAllWithCouponByUserId(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM UserCoupon uc WHERE uc.userId = :userId")
    void deleteByUserId(Long userId);
}
