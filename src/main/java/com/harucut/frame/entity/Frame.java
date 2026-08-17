package com.harucut.frame.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.converter.BackgroundConverter;
import com.harucut.frame.enums.FrameType;
import com.harucut.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "frame")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Frame extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "frame_id")
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "preview_key", nullable = false, length = 1024)
    private String previewKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "frame_type", nullable = false, length = 32)
    private FrameType frameType;

    @Convert(converter = BackgroundConverter.class)
    @Column(nullable = false, length = 4000)
    private BackgroundAttributes background;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "is_system", nullable = false)
    private boolean isSystem;

    @OneToMany(mappedBy = "frame", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FrameComponent> components = new ArrayList<>();

    private Frame(String title, String description, String previewKey,
                  FrameType frameType, BackgroundAttributes background, User user, boolean isSystem) {
        this.title = title;
        this.description = description;
        this.previewKey = previewKey;
        this.frameType = frameType;
        this.background = background;
        this.user = user;
        this.isSystem = isSystem;
    }

    public static Frame owned(User user, String title, String description, String previewKey,
                              FrameType frameType, BackgroundAttributes background) {
        if (user == null) {
            throw new IllegalArgumentException("사용자 프레임에는 소유자가 필요하다");
        }
        return new Frame(title, description, previewKey, frameType, background, user, false);
    }

    public static Frame system(String title, String description, String previewKey,
                               FrameType frameType, BackgroundAttributes background) {
        return new Frame(title, description, previewKey, frameType, background, null, true);
    }

    public void updateMetadata(String title, String description,
                               BackgroundAttributes background, String previewKey) {
        this.title = title;
        this.description = description;
        this.background = background;
        this.previewKey = previewKey;
    }

    public void addComponent(FrameComponent component) {
        components.add(component);
        component.assignFrame(this);
    }

    // 전체 교체 수정의 앞단 — orphanRemoval이 지워진 컴포넌트를 DB에서 삭제한다
    public void clearComponents() {
        components.clear();
    }

    // addComponent가 유일한 추가 경로여야 하므로 원본 리스트를 내주지 않는다
    public List<FrameComponent> getComponents() {
        return Collections.unmodifiableList(components);
    }
}
