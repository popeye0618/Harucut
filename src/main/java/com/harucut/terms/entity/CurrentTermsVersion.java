package com.harucut.terms.entity;

import com.harucut.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "current_terms_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_current_terms_version_terms_id",
                columnNames = "terms_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrentTermsVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "current_terms_version_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_version_id", nullable = false)
    private TermsVersion termsVersion;

    private CurrentTermsVersion(Terms terms, TermsVersion termsVersion) {
        this.terms = terms;
        this.termsVersion = termsVersion;
    }

    // terms를 따로 받지 않는다 - 버전이 속한 약관을 그대로 물려받는다
    public static CurrentTermsVersion pointTo(TermsVersion version) {
        return new CurrentTermsVersion(version.getTerms(), version);
    }

    // 개정 시 포인터만 갱신
    public void repoint(TermsVersion newVersion) {
        this.termsVersion = newVersion;
    }
}
