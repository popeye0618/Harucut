package com.harucut.frame.attributes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BackgroundAttributes.Color.class, name = "COLOR"),
        @JsonSubTypes.Type(value = BackgroundAttributes.Image.class, name = "IMAGE")
})
public sealed interface BackgroundAttributes permits BackgroundAttributes.Color, BackgroundAttributes.Image {

    record Color(String value) implements BackgroundAttributes {

        public Color {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("배경 색상 value는 비어 있을 수 없다");
            }
        }
    }

    record Image(
            String key,
            Double opacity,
            @JsonProperty(access = JsonProperty.Access.READ_ONLY)
            @JsonInclude(JsonInclude.Include.NON_NULL)
            String url
    ) implements BackgroundAttributes {
        public Image {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("배경 이미지 key는 비어 있을 수 없다");
            }
            if (opacity == null) {
                throw new IllegalArgumentException("opacity는 필수다");
            }
        }

        public Image withUrl(String url) {
            return new Image(key, opacity, url);
        }
    }
}
