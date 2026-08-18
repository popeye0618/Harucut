package com.harucut.payment.config;

import com.harucut.payment.gateway.PaymentGateway;
import com.harucut.payment.gateway.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/*
 * @Profile("!local & !test") 조건 자체는 단위 테스트로 못 잡는다.
 * 여기서는 생성자의 판정만 검증한다 — 어느 프로파일에서 이 가드가 뜨는지는 설정의 몫.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MockGatewayGuard")
class MockGatewayGuardTest {

    @Mock
    private PaymentGateway paymentGateway;

    @Test
    @DisplayName("mock 게이트웨이가 잡혀 있으면 기동을 실패시킨다")
    void rejectsMockGateway() {
        given(paymentGateway.provider()).willReturn(PgProvider.MOCK);

        assertThatThrownBy(() -> new MockGatewayGuard(paymentGateway))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("mock이 아니면 통과한다")
    void allowsRealGateway() {
        given(paymentGateway.provider()).willReturn(PgProvider.TOSS);

        assertThatCode(() -> new MockGatewayGuard(paymentGateway))
                .doesNotThrowAnyException();
    }
}
