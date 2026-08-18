package com.harucut.payment.entity;

import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.subscription.enums.PlanTier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentOrder")
class PaymentOrderTest {

    @Test
    @DisplayName("생성 직후 상태는 CREATED다")
    void startsCreated() {
        assertThat(order().getStatus()).isEqualTo(OrderStatus.CREATED);
    }

    @Test
    @DisplayName("생성하면 publicId가 12자로 채워진다")
    void publicIdIsFilled() {
        assertThat(order().getPublicId()).hasSize(12);
    }

    @Test
    @DisplayName("요청받은 값을 그대로 담는다")
    void keepsGivenValues() {
        assertThat(order())
                .extracting(PaymentOrder::getUserId, PaymentOrder::getTargetTier,
                        PaymentOrder::getAmount, PaymentOrder::getOrderType, PaymentOrder::getIdempotencyKey)
                .containsExactly(1L, PlanTier.PLUS, 3900, OrderType.INITIAL, "idem-1");
    }

    @Test
    @DisplayName("markPaid하면 PAID가 된다")
    void markPaid() {
        PaymentOrder order = order();

        order.markPaid();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("markFailed하면 FAILED가 된다")
    void markFailed() {
        PaymentOrder order = order();

        order.markFailed();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
    }

    private PaymentOrder order() {
        return PaymentOrder.createInitial(1L, PlanTier.PLUS, 3900, "idem-1");
    }
}
