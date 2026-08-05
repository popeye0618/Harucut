package com.harucut.common.utils;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public class PageRequests {

    private PageRequests() {
    }

    public static Pageable of(int page, int size) {
        if (page < 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "page must be 0 or greater.");
        }
        if (size < 1) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE, "size must be 1 or greater.");
        }

        return PageRequest.of(page, size);
    }
}
