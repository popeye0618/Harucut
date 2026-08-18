package com.harucut.terms.dto;

import com.harucut.terms.entity.TermsAgreementHistory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "동의 이력 한 건 (법적 증빙용). 이 기록은 수정·삭제되지 않는다")
public record TermsAgreementHistoryResponse(

        @Schema(description = "약관 코드", example = "tos")
        String code,

        @Schema(description = "동의·철회 당시의 버전. 지금 최신 버전과 다를 수 있다", example = "1")
        int version,

        @Schema(description = "`true` 동의 · `false` 철회", example = "true")
        boolean agreed,

        @Schema(description = "동의·철회 시각", example = "2026-03-14T09:12:00")
        LocalDateTime createdAt
) {
    public static TermsAgreementHistoryResponse from(TermsAgreementHistory history) {
        return new TermsAgreementHistoryResponse(history.getCode(), history.getVersion(),
                history.isAgreed(), history.getCreatedAt());
    }
}
