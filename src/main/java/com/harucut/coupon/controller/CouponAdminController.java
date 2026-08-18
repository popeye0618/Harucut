package com.harucut.coupon.controller;

import com.harucut.common.response.Response;
import com.harucut.coupon.dto.CouponAdminResponse;
import com.harucut.coupon.dto.CouponCreateRequest;
import com.harucut.coupon.service.CouponAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/coupons")
@PreAuthorize("hasRole('ADMIN')")
public class CouponAdminController {

    private final CouponAdminService couponAdminService;

    @PostMapping
    public Response<Void> create(@Valid @RequestBody CouponCreateRequest request) {
        couponAdminService.create(request);
        return Response.ok();
    }

    @GetMapping
    public Response<List<CouponAdminResponse>> coupons() {
        return Response.ok(couponAdminService.getAll());
    }

    @PatchMapping("/{publicId}/deactivate")
    public Response<Void> deactivate(@PathVariable String publicId) {
        couponAdminService.deactivate(publicId);
        return Response.ok();
    }
}
