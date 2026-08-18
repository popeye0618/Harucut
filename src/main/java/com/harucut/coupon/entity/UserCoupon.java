package com.harucut.coupon.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import com.harucut.coupon.enums.UserCouponStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_coupon",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_coupon_user_id_coupon_id",
                columnNames = {"user_id", "coupon_id"}
        ),
        indexes = @Index(name = "idx_user_coupon_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCoupon extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_coupon_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 12)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coupon_id", nullable = false)
    private Coupon coupon;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserCouponStatus status;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;

    private UserCoupon(Long userId, Coupon coupon, UserCouponStatus status, LocalDateTime redeemedAt) {
        this.publicId = PublicIds.generate();
        this.userId = userId;
        this.coupon = coupon;
        this.status = status;
        this.redeemedAt = redeemedAt;
    }

    // 즉시 개시
    public static UserCoupon redeemed(Coupon coupon, Long userId, LocalDateTime now) {
        return new UserCoupon(userId, coupon, UserCouponStatus.REDEEMED, now);
    }

    // 현 주기 후 예약
    public static UserCoupon reserved(Coupon coupon, Long userId, LocalDateTime now) {
        return new UserCoupon(userId, coupon, UserCouponStatus.RESERVED, now);
    }

    // 예약된 grant 개시 (RESERVED -> REDEEMED)
    public void markRedeemed() {
        this.status = UserCouponStatus.REDEEMED;
    }
}
