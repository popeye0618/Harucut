package com.harucut.frame.entity;

import com.harucut.common.entity.BaseEntity;
import com.harucut.frame.converter.StyleMapConverter;
import com.harucut.frame.enums.ComponentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Entity
@Table(name = "frame_component")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FrameComponent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "frame_component_id")
    private Long id;

    @Column(nullable = false, length = 1024)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ComponentType type;

    private double x;
    private double y;

    private Double width;
    private Double height;
    private Double scale;

    private double rotation;

    @Column(name = "z_index")
    private int zIndex;

    @Convert(converter = StyleMapConverter.class)
    @Column(name = "style_json", length = 4000)
    private Map<String, Object> style;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "frame_id")
    private Frame frame;

    @Builder
    private FrameComponent(String source, ComponentType type, double x, double y,
                           Double width, Double height, Double scale, double rotation,
                           int zIndex, Map<String, Object> style) {
        this.source = source;
        this.type = type;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.scale = scale;
        this.rotation = rotation;
        this.zIndex = zIndex;
        this.style = style == null ? Map.of() : style;
    }

    // Frame.addComponent만 부르도록 package-private — 외부 패키지에서 연관관계를 임의로 끊을 수 없다
    void assignFrame(Frame frame) {
        this.frame = frame;
    }
}
