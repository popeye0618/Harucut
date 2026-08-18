package com.harucut.payment.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.payment.dto.SubscribeRequest;
import com.harucut.payment.service.PaymentService;
import com.harucut.subscription.dto.SubscriptionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/payments")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/subscribe")
    public Response<SubscriptionResponse> subscribe(@AuthenticationPrincipal AuthenticatedUser principal,
                                                    @Valid @RequestBody SubscribeRequest request) {
        return Response.ok(paymentService.subscribe(principal.publicId(), request));
    }
}
