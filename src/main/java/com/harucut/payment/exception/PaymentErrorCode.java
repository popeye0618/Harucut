package com.harucut.payment.exception;

import com.harucut.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PaymentErrorCode implements ErrorCode {

    BILLING_KEY_ISSUE_FAILED("PAY-001", HttpStatus.BAD_GATEWAY, "Failed to issue a billing key."),
    PAYMENT_FAILED("PAY-002", HttpStatus.PAYMENT_REQUIRED, "Payment failed."),
    ALREADY_SUBSCRIBED("PAY-003", HttpStatus.CONFLICT, "Already subscribed to a paid plan."),
    DUPLICATE_PAYMENT("PAY-006", HttpStatus.CONFLICT, "Duplicate payment request."),
    INVALID_TARGET_PLAN("PAY-007", HttpStatus.BAD_REQUEST, "Invalid target plan tier."),
    WEBHOOK_SIGNATURE_INVALID("PAY-008", HttpStatus.BAD_REQUEST, "Invalid webhook signature.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
