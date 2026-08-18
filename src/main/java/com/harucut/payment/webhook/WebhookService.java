package com.harucut.payment.webhook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class WebhookService {

    public void handle(String rawBody) {
        log.info("[payment] 웹훅 수신. bodyLength={}", rawBody.length());
    }
}
