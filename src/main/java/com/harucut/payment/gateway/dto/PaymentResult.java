package com.harucut.payment.gateway.dto;

import java.time.LocalDateTime;

public record PaymentResult(
        boolean success,
        String pgTransactionId,
        LocalDateTime approvedAt,
        String failureCode,
        String failureMessage
) {
    public static PaymentResult success(String pgTransactionId, LocalDateTime approvedAt) {
        return new PaymentResult(true, pgTransactionId, approvedAt, null, null);
    }

    public static PaymentResult failure(String failureCode, String failureMessage) {
        return new PaymentResult(false, null, null, failureCode, failureMessage);
    }
}
