package com.harucut.coupon.controller;

import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.common.response.Response;
import com.harucut.coupon.dto.MyCouponResponse;
import com.harucut.coupon.dto.RedeemRequest;
import com.harucut.coupon.dto.RedeemResponse;
import com.harucut.coupon.service.CouponService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/coupons")
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/redeem")
    public Response<RedeemResponse> redeem(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @Valid @RequestBody RedeemRequest request) {
        return Response.ok(couponService.redeem(principal.publicId(), request.code()));
    }

    @GetMapping
    public Response<List<MyCouponResponse>> myCoupons(@AuthenticationPrincipal AuthenticatedUser principal) {
        return Response.ok(couponService.getMyCoupons(principal.publicId()));
    }
}
