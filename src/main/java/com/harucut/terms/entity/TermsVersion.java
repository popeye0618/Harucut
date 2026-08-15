package com.harucut.terms.entity;

import com.harucut.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "terms_version",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_terms_version_terms_id_version",
                columnNames = {"terms_id", "version"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsVersion extends BaseEntity {

    private static final int FIRST_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_version_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    private TermsVersion(Terms terms, int version, String content) {
        this.terms = terms;
        this.version = version;
        this.content = content;
    }

    public static TermsVersion first(Terms terms, String content) {
        return new TermsVersion(terms, FIRST_VERSION, content);
    }

    public static TermsVersion next(TermsVersion latest, String content) {
        return new TermsVersion(latest.terms, latest.version + 1, content);
    }
}
