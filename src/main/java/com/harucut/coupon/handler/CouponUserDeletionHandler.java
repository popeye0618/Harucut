package com.harucut.coupon.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.coupon.repository.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CouponUserDeletionHandler implements UserDeletionHandler {

    private final UserCouponRepository userCouponRepository;

    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        userCouponRepository.deleteByUserId(userId);
    }
}
