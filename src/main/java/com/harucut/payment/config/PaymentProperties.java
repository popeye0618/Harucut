package com.harucut.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
        @DefaultValue Gateway gateway,
        @DefaultValue Mock mock,
        @DefaultValue("3") int graceDays
) {

    public record Gateway(
            @DefaultValue("mock") String provider
    ){
    }

    public record Mock(
            @DefaultValue("false") boolean failCharge
    ){
    }
}
