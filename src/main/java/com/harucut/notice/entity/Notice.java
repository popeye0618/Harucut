package com.harucut.notice.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.common.utils.PublicIds;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notice",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notice_public_id",
                columnNames = "public_id"
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notice_id")
    private Long id;

    @Column(name = "public_id", nullable = false, length = 12)
    private String publicId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public Notice(String title, String content, boolean pinned) {
        this.publicId = PublicIds.generate();
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.published = false;
    }

    public void publish(LocalDateTime now) {
        this.published = true;
        this.publishedAt = now;
    }

    public void softDelete(LocalDateTime now) {
        this.deletedAt = now;
    }

    public void update(String title, String content, boolean pinned) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
    }

    public void unPublish() {
        this.published = false;
        this.publishedAt = null;
    }
}
