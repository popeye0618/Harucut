package com.harucut.payment.gateway.dto;

public record BillingChargeCommand(
        String billingKeyValue,
        String orderKey,
        int amount,
        String orderName,
        String customerKey
) {
}
