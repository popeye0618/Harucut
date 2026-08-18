package com.harucut.coupon.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.coupon.dto.CouponAdminResponse;
import com.harucut.coupon.dto.CouponCreateRequest;
import com.harucut.coupon.entity.Coupon;
import com.harucut.coupon.exception.CouponErrorCode;
import com.harucut.coupon.repository.CouponRepository;
import com.harucut.subscription.enums.PlanTier;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponAdminService {

    private final CouponRepository couponRepository;

    @Transactional
    public void create(CouponCreateRequest request) {
        if (request.grantTier() == PlanTier.BASIC) {
            throw new BusinessException(CouponErrorCode.INVALID_GRANT_TIER);
        }

        if (couponRepository.existsByCode(Coupon.normalizeCode(request.code()))) {
            throw new BusinessException(CouponErrorCode.COUPON_CODE_DUPLICATED);
        }

        try {
            couponRepository.saveAndFlush(Coupon.create(
                    request.name(), request.code(), request.grantTier(),
                    request.maxRedemptions(), request.validUntil()
            ));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(CouponErrorCode.COUPON_CODE_DUPLICATED);
        }
    }

    @Transactional(readOnly = true)
    public List<CouponAdminResponse> getAll() {
        return couponRepository.findAllByOrderByIdDesc().stream()
                .map(CouponAdminResponse::from)
                .toList();
    }

    @Transactional
    public void deactivate(String publicId) {
        Coupon coupon = couponRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(CouponErrorCode.COUPON_NOT_FOUND));

        coupon.deactivate();
    }
}
