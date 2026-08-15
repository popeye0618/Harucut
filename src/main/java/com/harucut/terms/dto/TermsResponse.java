package com.harucut.terms.dto;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsVersion;

public record TermsResponse(
        String code,
        String title,
        boolean required,
        int version,
        String content
) {

    public static TermsResponse from(CurrentTermsVersion current) {
        Terms terms = current.getTerms();
        TermsVersion version = current.getTermsVersion();

        return new TermsResponse(terms.getCode(), terms.getTitle(), terms.isRequired(),
                version.getVersion(), version.getContent());
    }
}
