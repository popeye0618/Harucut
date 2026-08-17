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

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    private UserMedia(User user, String s3Key, String displayName) {
        this.user = user;
        this.s3Key = s3Key;
        this.displayName = displayName;
    }

    public static UserMedia of(User user, String s3Key, String displayName) {
        if (user == null) {
            throw new IllegalArgumentException("미디어에는 소유자가 필요하다");
        }
        return new UserMedia(user, s3Key, displayName);
    }

    public void changeDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
