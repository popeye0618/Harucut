package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.enums.TermsAgreementStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "약관별 내 동의 상태")
public record TermsAgreementStatusResponse(

        @Schema(description = "약관 코드", example = "tos")
        String code,

        @Schema(description = "제목", example = "이용약관")
        String title,

        @Schema(description = "필수 동의 여부", example = "true")
        boolean required,

        @Schema(description = """
                `AGREED` 최신 버전에 동의함 ·
                `NEEDS_RECONSENT` 동의했지만 그 뒤 약관이 개정됨 ·
                `NOT_AGREED` 동의한 적이 없거나 철회함""",
                example = "NEEDS_RECONSENT")
        TermsAgreementStatus status,

        @Schema(description = "내가 동의한 버전. **동의한 적이 없거나 철회했으면 키 자체가 없다**", example = "1")
        Integer agreedVersion,

        @Schema(description = "현재 최신 버전. `agreedVersion` 과 다르면 개정된 것이다", example = "2")
        int latestVersion
) {
    public static TermsAgreementStatusResponse of(CurrentTermsVersion current, TermsAgreement agreement) {
        Terms terms = current.getTerms();
        int latestVersion = current.getTermsVersion().getVersion();
        Integer agreedVersion = (agreement != null && agreement.isAgreed())
                ? agreement.getAgreedVersion()
                : null;
        return new TermsAgreementStatusResponse(
                terms.getCode(), terms.getTitle(), terms.isRequired(),
                TermsAgreementStatus.of(agreedVersion, latestVersion),
                agreedVersion, latestVersion);
    }
}
