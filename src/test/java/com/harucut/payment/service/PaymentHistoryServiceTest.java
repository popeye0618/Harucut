package com.harucut.payment.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.common.response.PageResponse;
import com.harucut.payment.dto.PaymentHistoryResponse;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.payment.enums.OrderStatus;
import com.harucut.payment.enums.OrderType;
import com.harucut.payment.repository.PaymentOrderRepository;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentHistoryService")
class PaymentHistoryServiceTest {

    private static final String PUBLIC_ID = "user-pub-001";
    private static final Long USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    private PaymentHistoryService service;

    @BeforeEach
    void setUp() {
        service = new PaymentHistoryService(userRepository, paymentOrderRepository);
    }

    @Test
    @DisplayName("없는 사용자는 GEN-031이다")
    void unknownUser() {
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyHistory(PUBLIC_ID, 0, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("page가 음수면 GEN-002이고 주문을 조회하지 않는다")
    void negativePage() {
        givenUser();

        assertThatThrownBy(() -> service.getMyHistory(PUBLIC_ID, -1, 10))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(GlobalErrorCode.INVALID_INPUT_VALUE);

        then(paymentOrderRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("주문이 응답으로 매핑된다 — orderId에는 publicId가 들어간다")
    void mapsOrderToResponse() {
        givenUser();
        PaymentOrder order = PaymentOrder.createInitial(USER_ID, PlanTier.PLUS, 3900, "idem-1");
        order.markPaid();
        given(paymentOrderRepository.findByUserIdOrderByIdDesc(eq(USER_ID), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(order), PageRequest.of(0, 10), 1));

        PageResponse<PaymentHistoryResponse> response = service.getMyHistory(PUBLIC_ID, 0, 10);

        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.orderId()).isEqualTo(order.getPublicId());
            assertThat(item.planTier()).isEqualTo(PlanTier.PLUS);
            assertThat(item.amount()).isEqualTo(3900);
            assertThat(item.orderType()).isEqualTo(OrderType.INITIAL);
            assertThat(item.status()).isEqualTo(OrderStatus.PAID);
        });
    }

    private void givenUser() {
        User user = UserFixtures.localUser("history@harucut.com", "encoded");
        ReflectionTestUtils.setField(user, "id", USER_ID);
        given(userRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(user));
    }
}
