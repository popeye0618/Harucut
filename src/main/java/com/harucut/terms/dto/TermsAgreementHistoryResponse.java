package com.harucut.terms.dto;

import com.harucut.terms.entity.TermsAgreementHistory;

import java.time.LocalDateTime;

public record TermsAgreementHistoryResponse(String code, int version, boolean agreed,
                                            LocalDateTime createdAt) {

    public static TermsAgreementHistoryResponse from(TermsAgreementHistory history) {
        return new TermsAgreementHistoryResponse(history.getCode(), history.getVersion(),
                history.isAgreed(), history.getCreatedAt());
    }
}
