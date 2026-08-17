package com.harucut.subscription.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.subscription.dto.SubscriptionResponse;
import com.harucut.subscription.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/subscriptions")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @GetMapping
    public Response<SubscriptionResponse> mySubscription(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(subscriptionService.getMySubscription(principal.publicId()));
    }

    @PostMapping("/cancel")
    public Response<Void> cancel(@AuthenticationPrincipal AuthenticatedUser principal) {
        subscriptionService.cancelAutoRenew(principal.publicId());
        return Response.ok();
    }
}
