package com.harucut.terms.enums;

public enum TermsAgreementStatus {
    AGREED,
    NEEDS_RECONSENT,
    NOT_AGREED;

    public static TermsAgreementStatus of(Integer agreedVersion, int latestVersion) {
        if (agreedVersion == null) {
            return NOT_AGREED;
        }
        return agreedVersion == latestVersion ? AGREED : NEEDS_RECONSENT;
    }
}
