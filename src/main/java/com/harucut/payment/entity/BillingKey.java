package com.harucut.payment.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.gateway.PgProvider;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "billing_key",
        indexes = @Index(name = "idx_billing_key_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingKey extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "billing_key_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "pg_provider", nullable = false, length = 20)
    private PgProvider pgProvider;

    @Column(name = "billing_key_value", nullable = false)
    private String billingKeyValue;

    @Column(name = "masked_card", length = 32)
    private String maskedCard;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BillingKeyStatus status;

    private BillingKey(Long userId, PgProvider pgProvider, String billingKeyValue, String maskedCard) {
        this.userId = userId;
        this.pgProvider = pgProvider;
        this.billingKeyValue = billingKeyValue;
        this.maskedCard = maskedCard;
        this.status = BillingKeyStatus.ACTIVE;
    }

    public static BillingKey issue(Long userId, PgProvider pgProvider, String billingKeyValue, String maskedCard) {
        return new BillingKey(userId, pgProvider, billingKeyValue, maskedCard);
    }

    public void delete() {
        this.status = BillingKeyStatus.DELETED;
    }
}
