package com.harucut.payment.dto;

import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.subscription.enums.PlanTier;

import java.time.LocalDateTime;

public record PaymentHistoryResponse(
        String orderId,
        PlanTier planTier,
        int amount,
        OrderType orderType,
        OrderStatus status,
        LocalDateTime createdAt
) {
    public static PaymentHistoryResponse from(PaymentOrder order) {
        return new PaymentHistoryResponse(
                order.getPublicId(),
                order.getTargetTier(),
                order.getAmount(),
                order.getOrderType(),
                order.getStatus(),
                order.getCreatedAt()
        );
    }
}
