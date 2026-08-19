package com.harucut.payment.controller;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.response.Response;
import com.harucut.config.openapi.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import com.harucut.payment.exception.PaymentErrorCode;
import com.harucut.payment.webhook.PaymentWebhookVerifier;
import com.harucut.payment.webhook.WebhookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "결제 웹훅")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments")
public class PaymentWebhookController {

    private final PaymentWebhookVerifier webhookVerifier;
    private final WebhookService webhookService;

    @Operation(
            summary = "결제 웹훅",
            description = """
                    **PG 사가 호출한다. 프론트가 부를 API 가 아니다.**

                    본문을 **파싱하지 않고 원문 문자열 그대로** 받는다. 서명은 원문 바이트를 대상으로
                    검증해야 하는데, 객체로 바꿨다가 다시 문자열로 만들면 필드 순서나 공백이 달라져
                    서명이 깨지기 때문이다.

                    현재는 Mock 게이트웨이가 붙어 있어 서명 검증이 항상 통과하고,
                    본문 처리는 골격만 있다. 실제 PG 연동 시 채운다.
                    """)
    @ApiErrors("PAY-008: 서명 검증 실패")
    @PostMapping("/webhook")
    public Response<Void> webhook(
            @Parameter(description = "PG 가 만든 서명. 없으면 검증에 실패한다") @RequestHeader(name = "X-Signature", required = false) String signature,
            @RequestBody String rawBody
    ) {
        if (!webhookVerifier.verify(rawBody, signature)) {
            throw new BusinessException(PaymentErrorCode.WEBHOOK_SIGNATURE_INVALID);
        }
        webhookService.handle(rawBody);
        return Response.ok();
    }
}
