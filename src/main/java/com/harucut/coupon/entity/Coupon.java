package com.harucut.coupon.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import com.harucut.subscription.enums.PlanTier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(
        name = "coupon",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "coupon_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 12)
    private String publicId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 32)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_tier", nullable = false, length = 20)
    private PlanTier grantTier;

    // null = 무제한
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    // null = 무기한
    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "redeemed_count", nullable = false)
    private int redeemedCount;

    private Coupon(String name, String code, PlanTier grantTier, Integer maxRedemptions, LocalDateTime validUntil) {
        this.publicId = PublicIds.generate();
        this.name = name;
        this.code = code;
        this.grantTier = grantTier;
        this.maxRedemptions = maxRedemptions;
        this.validUntil = validUntil;
        this.active = true;
        this.redeemedCount = 0;
    }

    public static Coupon create(String name, String code, PlanTier grantTier, Integer maxRedemptions, LocalDateTime validUntil) {
        return new Coupon(name, normalizeCode(code), grantTier, maxRedemptions, validUntil);
    }

    public static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isRedeemable(LocalDateTime now) {
        return active && (validUntil == null || !now.isAfter(validUntil));
    }
}
