package com.harucut.terms.entity;

import com.harucut.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "terms_agreement_history",
        indexes = @Index(name = "idx_terms_agreement_history_user_id", columnList = "user_id")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsAgreementHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_agreement_history_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // termsId 대신 불변 code를 스냅샷으로 담는다 - 행 하나가 자기완결적인 기록이 된다
    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private int version;

    @Column(nullable = false)
    private boolean agreed;

    private TermsAgreementHistory(Long userId, String code, int version, boolean agreed) {
        this.userId = userId;
        this.code = code;
        this.version = version;
        this.agreed = agreed;
    }

    public static TermsAgreementHistory of(Long userId, String code, int version, boolean agreed) {
        return new TermsAgreementHistory(userId, code, version, agreed);
    }
}
