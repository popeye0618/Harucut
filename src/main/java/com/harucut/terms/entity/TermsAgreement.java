package com.harucut.terms.entity;

import com.harucut.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "terms_agreement",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_terms_agreement_user_id_terms_id",
                columnNames = {"user_id", "terms_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_agreement_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "terms_id", nullable = false)
    private Terms terms;

    @Column(name = "agreed_version", nullable = false)
    private int agreedVersion;

    @Column(nullable = false)
    private boolean agreed;

    private TermsAgreement(Long userId, Terms terms, int agreedVersion, boolean agreed) {
        this.userId = userId;
        this.terms = terms;
        this.agreedVersion = agreedVersion;
        this.agreed = agreed;
    }

    public static TermsAgreement of(Long userId, Terms terms, int agreedVersion, boolean agreed) {
        return new TermsAgreement(userId, terms, agreedVersion, agreed);
    }

    public void update(int agreedVersion, boolean agreed) {
        this.agreedVersion = agreedVersion;
        this.agreed = agreed;
    }
}
