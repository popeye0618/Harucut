package com.harucut.subscription.controller;

import com.harucut.common.response.Response;
import com.harucut.subscription.dto.SubscriptionAdminResponse;
import com.harucut.subscription.service.SubscriptionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasRole('ADMIN')")
public class SubscriptionAdminController {

    private final SubscriptionAdminService subscriptionAdminService;

    @GetMapping("/{userId}")
    public Response<SubscriptionAdminResponse> subscription(@PathVariable Long userId) {
        return Response.ok(subscriptionAdminService.getSubscription(userId));
    }
}