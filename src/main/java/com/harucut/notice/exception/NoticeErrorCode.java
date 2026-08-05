package com.harucut.notice.exception;

import com.harucut.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NoticeErrorCode implements ErrorCode {

    NOTICE_NOT_FOUND("NOTICE-001", HttpStatus.NOT_FOUND, "The requested notice does not exist or is not published.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
