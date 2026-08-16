package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsVersion;

public record TermsAdminResponse(Long termsId, String code, String title, boolean required,
                                 boolean active, int latestVersion, String content) {

    public static TermsAdminResponse from(CurrentTermsVersion current) {
        Terms terms = current.getTerms();
        TermsVersion version = current.getTermsVersion();

        return new TermsAdminResponse(terms.getId(), terms.getCode(), terms.getTitle(),
                terms.isRequired(), terms.isActive(), version.getVersion(), version.getContent());
    }
}
