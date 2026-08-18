package com.harucut.payment.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.subscription.enums.PlanTier;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "payment_order",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_order_idemptency_key",
                columnNames = "idempotency_key"
        ),
        indexes = @Index(name = "idx_payment_order_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOrder extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_order_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 12)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_tier", nullable = false, length = 20)
    private PlanTier targetTier;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 20)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    private PaymentOrder(Long userId, PlanTier targetTier, int amount, OrderType orderType, String idempotencyKey) {
        this.publicId = PublicIds.generate();
        this.userId = userId;
        this.targetTier = targetTier;
        this.amount = amount;
        this.orderType = orderType;
        this.status = OrderStatus.CREATED;
        this.idempotencyKey = idempotencyKey;
    }

    public static PaymentOrder createInitial(Long userId, PlanTier planTier, int amount, String idempotencyKey) {
        return new PaymentOrder(userId, planTier, amount, OrderType.INITIAL, idempotencyKey);
    }

    public static PaymentOrder createRenewal(Long userId, PlanTier planTier, int amount, String idempotencyKey) {
        return new PaymentOrder(userId, planTier, amount, OrderType.RENEWAL, idempotencyKey);
    }

    public void markPaid() {
        this.status = OrderStatus.PAID;
    }

    public void markFailed() {
        this.status = OrderStatus.FAILED;
    }

    public void markCharging() {
        this.status = OrderStatus.IN_PROGRESS;
    }
}
