package com.harucut.payment.controller;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.Response;
import com.harucut.payment.exception.PaymentErrorCode;
import com.harucut.payment.webhook.PaymentWebhookVerifier;
import com.harucut.payment.webhook.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private final PaymentWebhookVerifier webhookVerifier;
    private final WebhookService webhookService;

    @PostMapping("/webhook")
    public Response<Void> webhook(
            @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        if (!webhookVerifier.verify(rawBody, signature)) {
            throw new BusinessException(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        webhookService.handle(rawBody);
        return Response.ok();
    }
}
