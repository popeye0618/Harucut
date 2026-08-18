package com.harucut.payment.webhook;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.gateway.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentWebhookVerifier implements PaymentWebhookVerifier {

    @Override
    public boolean verify(String rawBody, String signature) {
        return true;
    }
}
