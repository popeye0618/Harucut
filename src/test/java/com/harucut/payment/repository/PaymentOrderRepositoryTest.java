package com.harucut.payment.repository;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.payment.entity.PaymentOrder;
import com.harucut.subscription.enums.PlanTier;
import com.harucut.support.FixedClockConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @AutoConfigureJson: frame 엔티티의 AttributeConverter가 앱 ObjectMapper를 주입받는데,
// Hibernate는 어떤 엔티티를 쓰든 메타모델 전체를 만들므로 모든 @DataJpaTest에 Jackson이 필요하다
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
class PaymentOrderRepositoryTest {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    /*
     * 클라이언트 생성 멱등 키의 최후 방어선.
     * 3단계의 서비스 검사가 경쟁 요청에 뚫려도 두 번째 INSERT는 여기서 막힌다.
     */
    @Test
    @DisplayName("같은 idempotencyKey로 두 번 저장하면 DB 제약에 걸린다")
    void rejectsDuplicateIdempotencyKey() {
        paymentOrderRepository.save(PaymentOrder.createInitial(1L, PlanTier.PLUS, 3900, "idem-1"));
        paymentOrderRepository.flush();

        PaymentOrder duplicate = PaymentOrder.createInitial(2L, PlanTier.PRO, 9900, "idem-1");

        assertThatThrownBy(() -> {
            paymentOrderRepository.save(duplicate);
            paymentOrderRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("키가 다르면 같은 사용자의 주문이 여러 개 저장된다")
    void allowsDifferentKeys() {
        paymentOrderRepository.save(PaymentOrder.createInitial(1L, PlanTier.PLUS, 3900, "idem-1"));
        paymentOrderRepository.save(PaymentOrder.createInitial(1L, PlanTier.PLUS, 3900, "idem-2"));
        paymentOrderRepository.flush();

        assertThat(paymentOrderRepository.count()).isEqualTo(2L);
    }

    @Test
    @DisplayName("내역 조회는 내 주문만, 최신 것부터 나온다")
    void historyIsMineNewestFirst() {
        paymentOrderRepository.save(PaymentOrder.createInitial(10L, PlanTier.PLUS, 3900, "hist-1"));
        paymentOrderRepository.save(PaymentOrder.createInitial(99L, PlanTier.PRO, 9900, "hist-other"));
        paymentOrderRepository.save(PaymentOrder.createInitial(10L, PlanTier.PRO, 9900, "hist-2"));

        Page<PaymentOrder> result =
                paymentOrderRepository.findByUserIdOrderByIdDesc(10L, PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(PaymentOrder::getIdempotencyKey)
                .containsExactly("hist-2", "hist-1");
    }
}
