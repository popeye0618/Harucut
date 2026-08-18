package com.harucut.payment.gateway;

import com.harucut.payment.gateway.dto.BillingChargeCommand;
import com.harucut.payment.gateway.dto.BillingKeyResult;
import com.harucut.payment.gateway.dto.IssueBillingKeyCommand;
import com.harucut.payment.gateway.dto.PaymentResult;

public interface PaymentGateway {

    PgProvider provider();

    BillingKeyResult issueBillingKey(IssueBillingKeyCommand command);

    PaymentResult charge(BillingChargeCommand command);
}
