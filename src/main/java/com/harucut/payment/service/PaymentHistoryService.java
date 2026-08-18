package com.harucut.payment.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.common.response.PageResponse;
import com.harucut.common.utils.PageRequests;
import com.harucut.payment.dto.PaymentHistoryResponse;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentHistoryService {

    private final UserRepository userRepository;
    private final PaymentOrderRepository paymentOrderRepository;

    public PageResponse<PaymentHistoryResponse> getMyHistory(String publicId, int page, int size) {
        User user = userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND, "User not found."));

        return PageResponse.from(
                paymentOrderRepository.findByUserIdOrderByIdDesc(user.getId(), PageRequests.of(page, size))
                        .map(PaymentHistoryResponse::from)
        );
    }
}
