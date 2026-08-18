package com.harucut.payment.webhook;

public interface PaymentWebhookVerifier {

    boolean verify(String rawBody, String signature);
}
