package com.harucut.payment.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.payment.repository.BillingKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 빌링키(카드 토큰)만 지운다. payment_order/payment는 개인정보가 아닌 결제 이력이고
// 회계·분쟁 대응, 전자상거래법상 보존 의무 때문에 남긴다 —
// user 행이 익명화된 채 남으므로 이력의 user_id는 계속 유효하다.
@Component
@RequiredArgsConstructor
public class PaymentDataDeletionHandler implements UserDeletionHandler {

    private final BillingKeyRepository billingKeyRepository;

    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        billingKeyRepository.deleteByUserId(userId);
    }
}
