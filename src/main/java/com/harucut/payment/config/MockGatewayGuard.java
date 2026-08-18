package com.harucut.payment.config;

import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.PgProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
public class MockGatewayGuard {

    public MockGatewayGuard(PaymentGateway paymentGateway) {
        if (paymentGateway.provider() == PgProvider.MOCK) {
            throw new IllegalStateException("Mock payment gateway must not be active outside local/test profiles.");
        }
    }
}
