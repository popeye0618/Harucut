package com.harucut.payment.repository;

import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    Page<PaymentOrder> findByUserIdOrderByIdDesc(Long userId, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);

    // 결과 미확정(만듦/긁는 중) 갱신 주문이 남아 있나 — 있으면 새 청구를 만들지 않는다
    boolean existsByUserIdAndOrderTypeAndStatusIn(Long userId, OrderType orderType, Collection<OrderStatus> statuses);
}
