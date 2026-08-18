package com.harucut.payment.entity;

import com.harucut.payment.enums.BillingKeyStatus;
import com.harucut.payment.gateway.PgProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BillingKey")
class BillingKeyTest {

    @Test
    @DisplayName("발급 직후 상태는 ACTIVE다")
    void startsActive() {
        assertThat(billingKey().getStatus()).isEqualTo(BillingKeyStatus.ACTIVE);
    }

    @Test
    @DisplayName("delete하면 행이 지워지는 게 아니라 상태만 DELETED가 된다")
    void deleteIsSoft() {
        BillingKey billingKey = billingKey();

        billingKey.delete();

        assertThat(billingKey.getStatus()).isEqualTo(BillingKeyStatus.DELETED);
        assertThat(billingKey.getBillingKeyValue()).isEqualTo("mock-bk-customer-1-123");
    }

    private BillingKey billingKey() {
        return BillingKey.issue(1L, PgProvider.MOCK, "mock-bk-customer-1-123", "**** **** **** 1234");
    }
}
