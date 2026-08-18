package com.harucut.payment.gateway.dto;

public record IssueBillingKeyCommand(
        String customerKey,
        String authKey
) {
}
