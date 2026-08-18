package com.harucut.payment.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import com.harucut.payment.enums.PaymentMethod;
import com.harucut.payment.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment",
        indexes = @Index(name = "idx_payment_payment_order_id", columnList = "payment_order_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 12)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder order;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "failure_message", length = 255)
    private String failureMessage;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    private Payment(PaymentOrder order, int amount, PaymentMethod method) {
        this.publicId = PublicIds.generate();
        this.order = order;
        this.amount = amount;
        this.method = method;
        this.status = PaymentStatus.REQUESTED;
    }

    public static Payment request(PaymentOrder order, int amount) {
        return new Payment(order, amount, PaymentMethod.BILLING_KEY);
    }

    public void approve(String pgTransactionId, LocalDateTime approvedAt) {
        this.status = PaymentStatus.APPROVED;
        this.pgTransactionId = pgTransactionId;
        this.approvedAt = approvedAt;
    }

    public void fail(String failureCode, String failureMessage) {
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }
}
