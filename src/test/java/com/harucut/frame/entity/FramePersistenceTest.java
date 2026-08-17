package com.harucut.frame.entity;

import com.harucut.config.JpaAuditingConfig;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.converter.BackgroundConverter;
import com.harucut.frame.converter.StyleMapConverter;
import com.harucut.frame.enums.ComponentType;
import com.harucut.frame.enums.FrameType;
import com.harucut.support.FixedClockConfig;
import com.harucut.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// AttributeConverter의 계약은 "왕복 무손실"이다 — 저장 → flush/clear → 재조회가 같은 값을 돌려줘야 한다.
// 실제 DB(H2)와 실제 Hibernate 메타모델을 통과시켜 컨버터 배선까지 함께 검증한다.
@DataJpaTest
@AutoConfigureJson
@Import({JpaAuditingConfig.class, FixedClockConfig.class})
@ActiveProfiles("test")
@DisplayName("Frame 영속성")
class FramePersistenceTest {

    private static final String IMAGE_KEY = "uploads/users/AbCdEf12Gh/components/bg.png";

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("배경 왕복")
    class BackgroundRoundTrip {

        @Test
        @DisplayName("COLOR 배경이 저장 후 재조회해도 같은 값이다")
        void colorRoundTrip() {
            Frame saved = em.persistFlushFind(
                    Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                            new BackgroundAttributes.Color("#FFE4E1")));

            assertThat(saved.getBackground()).isEqualTo(new BackgroundAttributes.Color("#FFE4E1"));
        }

        @Test
        @DisplayName("IMAGE 배경이 저장 후 재조회해도 같은 값이고 url은 null이다")
        void imageRoundTrip() {
            Frame saved = em.persistFlushFind(
                    Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                            new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null)));

            assertThat(saved.getBackground())
                    .isEqualTo(new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null));
        }
    }

    @Nested
    @DisplayName("스타일 왕복")
    class StyleRoundTrip {

        @Test
        @DisplayName("자유 형식 맵이 저장 후 재조회해도 같은 값이다")
        void styleMapRoundTrip() {
            Frame frame = systemFrame();
            frame.addComponent(component(1, Map.of("borderRadius", 8, "fontFamily", "Pretendard")));
            Long frameId = em.persistAndGetId(frame, Long.class);
            em.flush();
            em.clear();

            Frame found = em.find(Frame.class, frameId);

            assertThat(found.getComponents().get(0).getStyle())
                    .isEqualTo(Map.of("borderRadius", 8, "fontFamily", "Pretendard"));
        }

        @Test
        @DisplayName("style 없이 저장해도 재조회하면 빈 맵이다 — null이 아니다")
        void nullStyleBecomesEmptyMap() {
            Frame frame = systemFrame();
            frame.addComponent(FrameComponent.builder()
                    .source("uploads/photo.png").type(ComponentType.PHOTO).build());
            Long frameId = em.persistAndGetId(frame, Long.class);
            em.flush();
            em.clear();

            Frame found = em.find(Frame.class, frameId);

            assertThat(found.getComponents().get(0).getStyle()).isNotNull().isEmpty();
        }
    }

    @Nested
    @DisplayName("컴포넌트 생명주기 — cascade와 orphanRemoval")
    class ComponentLifecycle {

        @Test
        @DisplayName("프레임만 저장하면 컴포넌트가 함께 저장된다 (cascade)")
        void cascadePersistsComponents() {
            User owner = em.persist(User.localUser("owner@harucut.com", "encoded", "소유자"));
            Frame frame = Frame.owned(owner, "내 프레임", "설명", "uploads/p.png",
                    FrameType.CLASSIC, new BackgroundAttributes.Color("#FFF"));
            frame.addComponent(component(2, Map.of()));
            frame.addComponent(component(1, Map.of()));
            Long frameId = em.persistAndGetId(frame, Long.class);
            em.flush();
            em.clear();

            Frame found = em.find(Frame.class, frameId);

            assertThat(found.getComponents()).hasSize(2);
            assertThat(found.getComponents()).extracting(FrameComponent::getZIndex)
                    .containsExactlyInAnyOrder(1, 2);
            assertThat(found.getUser().getId()).isEqualTo(owner.getId());
        }

        @Test
        @DisplayName("clearComponents 후 flush하면 컴포넌트 행이 삭제된다 (orphanRemoval)")
        void clearComponentsDeletesRows() {
            Frame frame = systemFrame();
            frame.addComponent(component(1, Map.of()));
            frame.addComponent(component(2, Map.of()));
            Long frameId = em.persistAndGetId(frame, Long.class);
            em.flush();
            em.clear();

            Frame found = em.find(Frame.class, frameId);
            found.clearComponents();
            em.flush();
            em.clear();

            Long remaining = em.getEntityManager()
                    .createQuery("select count(c) from FrameComponent c", Long.class)
                    .getSingleResult();
            assertThat(remaining).isZero();
            assertThat(em.find(Frame.class, frameId)).isNotNull();
        }
    }

    @Nested
    @DisplayName("깨진 JSON — 두 컨버터의 의도된 비대칭")
    class CorruptJson {

        @Test
        @DisplayName("배경 JSON이 깨져 있으면 크게 실패한다 — 깨진 배경으로 그리는 척하지 않는다")
        void corruptBackgroundFailsLoud() {
            BackgroundConverter converter = new BackgroundConverter(objectMapper);

            assertThatThrownBy(() -> converter.convertToEntityAttribute("{broken"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("스타일 JSON이 깨져 있으면 빈 맵으로 조용히 내려앉는다 — 장식 때문에 프레임을 못 보여주면 안 된다")
        void corruptStyleDegrades() {
            StyleMapConverter converter = new StyleMapConverter(objectMapper);

            assertThat(converter.convertToEntityAttribute("{broken")).isEmpty();
        }
    }

    private static Frame systemFrame() {
        return Frame.system("기본", "설명", "uploads/p.png", FrameType.CLASSIC,
                new BackgroundAttributes.Color("#FFE4E1"));
    }

    private static FrameComponent component(int zIndex, Map<String, Object> style) {
        return FrameComponent.builder()
                .source("uploads/photo" + zIndex + ".png")
                .type(ComponentType.PHOTO)
                .x(120.5).y(220.0)
                .width(360.0).height(480.0)
                .scale(1.0).rotation(0.0)
                .zIndex(zIndex)
                .style(style)
                .build();
    }
}
