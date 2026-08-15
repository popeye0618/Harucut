package com.harucut.terms.exception;

import com.harucut.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TermsErrorCode implements ErrorCode {

    TERMS_NOT_FOUND("TERMS-001", HttpStatus.NOT_FOUND, "The requested terms do not exist or are inactive."),
    TERMS_CODE_DUPLICATED("TERMS-002", HttpStatus.CONFLICT, "Terms code already exists."),
    REQUIRED_TERMS_CANNOT_WITHDRAW("TERMS-003", HttpStatus.BAD_REQUEST, "Required terms cannot be withdrawn.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
