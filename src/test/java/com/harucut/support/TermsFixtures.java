package com.harucut.support;

import com.harucut.terms.entity.CurrentTermsVersion;
import com.harucut.terms.entity.Terms;
import com.harucut.terms.entity.TermsAgreement;
import com.harucut.terms.entity.TermsVersion;
import org.springframework.test.util.ReflectionTestUtils;

public final class TermsFixtures {

    private TermsFixtures() {
    }

    public static Terms terms(Long id, String code, boolean required) {
        Terms terms = Terms.create(code, code + " 제목", required);
        ReflectionTestUtils.setField(terms, "id", id);
        return terms;
    }

    public static TermsVersion version(Terms terms, int version) {
        TermsVersion termsVersion = TermsVersion.first(terms, terms.getCode() + " 본문 v" + version);
        ReflectionTestUtils.setField(termsVersion, "version", version);
        return termsVersion;
    }

    public static CurrentTermsVersion currentVersion(Terms terms, int version) {
        return CurrentTermsVersion.pointTo(version(terms, version));
    }

    public static TermsAgreement agreement(Long userId, Terms terms, int agreedVersion, boolean agreed) {
        return TermsAgreement.of(userId, terms, agreedVersion, agreed);
    }
}
