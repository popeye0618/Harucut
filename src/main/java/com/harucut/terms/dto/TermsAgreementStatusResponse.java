package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.enums.TermsAgreementStatus;

public record TermsAgreementStatusResponse(
        String code, String title, boolean required,
        TermsAgreementStatus status, Integer agreedVersion, int latestVersion
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