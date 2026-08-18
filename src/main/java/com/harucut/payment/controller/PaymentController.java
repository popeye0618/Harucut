package com.harucut.payment.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.PageResponse;
import com.harucut.common.response.Response;
import com.harucut.payment.dto.PaymentHistoryResponse;
import com.harucut.payment.dto.SubscribeRequest;
import com.harucut.payment.service.PaymentHistoryService;
import com.harucut.payment.service.PaymentService;
import com.harucut.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentHistoryService paymentHistoryService;

    @PostMapping("/subscribe")
    public Response<SubscriptionResponse> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody SubscribeRequest request) {
        return Response.ok(paymentService.subscribe(principal.publicId(), request));
    }

    @GetMapping
    public Response<PageResponse<PaymentHistoryResponse>> history(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return Response.ok(paymentHistoryService.getMyHistory(principal.publicId(), page, size));
    }
}
