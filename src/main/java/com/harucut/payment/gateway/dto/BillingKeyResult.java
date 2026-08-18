package com.harucut.payment.gateway.dto;

public record BillingKeyResult(
        boolean success,
        String billingKeyValue,
        String maskedCard,
        String failureCode,
        String failureMessage
) {

    public static BillingKeyResult success(String billingKeyValue, String maskedCard) {
        return new BillingKeyResult(true, billingKeyValue, maskedCard, null, null);
    }

    public static BillingKeyResult failure(String failureCode, String failureMessage) {
        return new BillingKeyResult(false, null, null, failureCode, failureMessage);
    }
}
