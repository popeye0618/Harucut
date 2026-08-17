package com.harucut.media.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserMedia extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "media_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // 한 파일 = 한 행. 같은 key가 두 번 등록되는 걸 DB가 마지막에 막는다
    @Column(name = "s3_key", nullable = false, length = 512, unique = true)
    private String s3Key;

    // 합성이 원본과 함께 만든 축소본. 썸네일이 생기기 전의 옛 행은 null로 남는다.
    // jobId 파생 값이라 unique는 걸지 않는다 — 충돌 경로가 없다
    @Column(name = "thumbnail_key", length = 512)
    private String thumbnailKey;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    private UserMedia(User user, String s3Key, String thumbnailKey, String displayName) {
        this.user = user;
        this.s3Key = s3Key;
        this.thumbnailKey = thumbnailKey;
        this.displayName = displayName;
    }

    public static UserMedia of(User user, String s3Key, String displayName) {
        return of(user, s3Key, null, displayName);
    }

    public static UserMedia of(User user, String s3Key, String thumbnailKey, String displayName) {
        if (user == null) {
            throw new IllegalArgumentException("미디어에는 소유자가 필요하다");
        }
        return new UserMedia(user, s3Key, thumbnailKey, displayName);
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
