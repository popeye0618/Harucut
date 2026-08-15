package com.harucut.terms.entity;

import com.harucut.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "terms",
        uniqueConstraints = @UniqueConstraint(name = "uk_terms_code", columnNames = "code")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Terms extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "terms_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean active;

    private Terms(String code, String title, boolean required) {
        this.code = code;
        this.title = title;
        this.required = required;
        this.active = true;
    }

    public static Terms create(String code, String title, boolean required) {
        return new Terms(code, title, required);
    }

    // 비활성화 (멱등). 현재 버전 포인터는 건드리지 않는다
    public void deactivate() {
        this.active = false;
    }
}
