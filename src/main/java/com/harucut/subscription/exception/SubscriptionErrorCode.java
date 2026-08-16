package com.harucut.subscription.exception;

import com.harucut.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SubscriptionErrorCode implements ErrorCode {

    PLAN_HISTORY_RETENTION_EXCEEDED("SUBS-002", HttpStatus.FORBIDDEN, "Requested history is beyond the plan's retention period."),
    PLAN_FRAME_RETENTION_EXCEEDED("SUBS-003", HttpStatus.FORBIDDEN, "The number of stored frames exceeds the limit for the current plan."),
    NO_ACTIVE_SUBSCRIPTION("SUBS-004", HttpStatus.NOT_FOUND, "No active subscription found."),
    ALREADY_CANCELED("SUBS-005", HttpStatus.CONFLICT, "The subscription's auto-renewal is already canceled."),
    NO_AUTO_RENEWAL_TO_CANCEL("SUBS-006", HttpStatus.CONFLICT, "There is no auto-renewal to cancel for this subscription.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}