package com.harucut.frame.exception;

import com.harucut.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FrameErrorCode implements ErrorCode {

    // 없는 프레임과 사용자 프레임 id 조작 시도가 같은 404다 — 관리자에게도 남의 프레임 존재를 확인시켜주지 않는다
    SYSTEM_FRAME_NOT_FOUND("FRAME-001", HttpStatus.NOT_FOUND, "The requested system frame does not exist.");

    private final String code;
    private final HttpStatus httpStatus;
    private final String message;
}
