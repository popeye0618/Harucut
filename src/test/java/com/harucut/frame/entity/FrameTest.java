package com.harucut.frame.entity;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;
import com.harucut.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Frame")
class FrameTest {

    private static final BackgroundAttributes COLOR = new BackgroundAttributes.Color("#FFE4E1");

    @Nested
    @DisplayName("팩토리 불변식 — isSystem ⇔ user 없음")
    class Factories {

        @Test
        @DisplayName("owned 프레임은 소유자를 갖고 시스템 프레임이 아니다")
        void ownedFrame() {
            User user = user();

            Frame frame = Frame.owned(user, "봄 여행", "설명", "uploads/p.png", FrameType.CLASSIC, COLOR);

            assertThat(frame.getUser()).isSameAs(user);
            assertThat(frame.isSystem()).isFalse();
        }

        @Test
        @DisplayName("system 프레임은 소유자가 없다")
        void systemFrame() {
            Frame frame = Frame.system("기본", "설명", "uploads/p.png", FrameType.WIDE, COLOR);

            assertThat(frame.getUser()).isNull();
            assertThat(frame.isSystem()).isTrue();
        }

        @Test
        @DisplayName("owned에 소유자 없이 오면 만들 수 없다 — 불변식의 마지막 방어선")
        void ownedRejectsNullUser() {
            assertThatThrownBy(() ->
                    Frame.owned(null, "제목", "설명", "uploads/p.png", FrameType.CLASSIC, COLOR))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("컴포넌트 컬렉션")
    class Components {

        @Test
        @DisplayName("addComponent는 리스트 추가와 역방향 참조를 함께 건다")
        void addComponentSyncsBothSides() {
            Frame frame = systemFrame();
            FrameComponent component = photoComponent(1);

            frame.addComponent(component);

            assertThat(frame.getComponents()).containsExactly(component);
            assertThat(component.getFrame()).isSameAs(frame);
        }

        @Test
        @DisplayName("clearComponents는 리스트를 비운다 — 전체 교체 수정의 앞단")
        void clearComponentsEmptiesList() {
            Frame frame = systemFrame();
            frame.addComponent(photoComponent(1));
            frame.addComponent(photoComponent(2));

            frame.clearComponents();

            assertThat(frame.getComponents()).isEmpty();
        }

        @Test
        @DisplayName("getComponents로 받은 리스트에 직접 추가할 수 없다 — addComponent 우회 차단")
        void componentsListIsUnmodifiable() {
            Frame frame = systemFrame();

            assertThatThrownBy(() -> frame.getComponents().add(photoComponent(1)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested
    @DisplayName("updateMetadata")
    class UpdateMetadata {

        @Test
        @DisplayName("제목·설명·배경·프리뷰·누끼만 바꾸고 타입·소유자·시스템 여부는 건드리지 않는다")
        void changesOnlyMetadata() {
            User user = user();
            Frame frame = Frame.owned(user, "옛 제목", "옛 설명", "uploads/old.png", FrameType.CLASSIC, COLOR);
            BackgroundAttributes newBackground = new BackgroundAttributes.Image("uploads/bg.png", 0.8, null);

            frame.updateMetadata("새 제목", "새 설명", newBackground, "uploads/new.png",
                    List.of(true, true, false, false));

            assertThat(frame.getTitle()).isEqualTo("새 제목");
            assertThat(frame.getDescription()).isEqualTo("새 설명");
            assertThat(frame.getBackground()).isEqualTo(newBackground);
            assertThat(frame.getPreviewKey()).isEqualTo("uploads/new.png");
            assertThat(frame.getCellCutouts()).containsExactly(true, true, false, false);
            // 안 바꾸는 것 — frameType 변경은 좌표 전제가 무너지므로 수정 경로에 존재하지 않는다
            assertThat(frame.getFrameType()).isEqualTo(FrameType.CLASSIC);
            assertThat(frame.getUser()).isSameAs(user);
            assertThat(frame.isSystem()).isFalse();
        }
    }

    @Nested
    @DisplayName("FrameComponent 빌더")
    class ComponentBuilder {

        @Test
        @DisplayName("style을 안 주면 null 대신 빈 맵으로 정규화된다")
        void normalizesNullStyle() {
            FrameComponent component = FrameComponent.builder()
                    .source("uploads/photo.png")
                    .type(ComponentType.PHOTO)
                    .build();

            assertThat(component.getStyle()).isNotNull().isEmpty();
        }
    }

    private static User user() {
        return User.localUser("user@harucut.com", "encoded", "하루컷");
    }

    private static Frame systemFrame() {
        return Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC, COLOR);
    }

    private static FrameComponent photoComponent(int zIndex) {
        return FrameComponent.builder()
                .source("uploads/photo" + zIndex + ".png")
                .type(ComponentType.PHOTO)
                .x(10.0).y(20.0)
                .zIndex(zIndex)
                .build();
    }
}
