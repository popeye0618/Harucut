package com.harucut.frame.attributes;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.ValueInstantiationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// 다형성 JSON은 wire 계약이므로 직접 만든 ObjectMapper가 아니라
// 애플리케이션 설정 그대로의 @JsonTest 슬라이스로 검증한다 (docs/testing.md)
@JsonTest
@ActiveProfiles("test")
@DisplayName("BackgroundAttributes 직렬화")
class BackgroundAttributesTest {

    private static final String IMAGE_KEY = "uploads/users/AbCdEf12Gh/components/bg.png";

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("COLOR")
    class ColorJson {

        @Test
        @DisplayName("type=COLOR JSON이 Color로 역직렬화된다")
        void deserializes() {
            BackgroundAttributes result = objectMapper.readValue(
                    "{\"type\":\"COLOR\",\"value\":\"#FFE4E1\"}", BackgroundAttributes.class);

            assertThat(result).isEqualTo(new BackgroundAttributes.Color("#FFE4E1"));
        }

        @Test
        @DisplayName("직렬화하면 객체에 없는 판별자 type이 JSON에 붙는다")
        void serializesWithTypeDiscriminator() {
            String json = objectMapper.writeValueAsString(new BackgroundAttributes.Color("#FFE4E1"));

            assertThat(json).isEqualTo("{\"type\":\"COLOR\",\"value\":\"#FFE4E1\"}");
        }
    }

    @Nested
    @DisplayName("IMAGE")
    class ImageJson {

        @Test
        @DisplayName("type=IMAGE JSON이 Image로 역직렬화되고 url은 null이다")
        void deserializes() {
            BackgroundAttributes result = objectMapper.readValue(
                    "{\"type\":\"IMAGE\",\"key\":\"" + IMAGE_KEY + "\",\"opacity\":0.8}",
                    BackgroundAttributes.class);

            assertThat(result).isEqualTo(new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null));
        }

        @Test
        @DisplayName("요청이 url을 실어 보내도 무시된다 — READ_ONLY")
        void ignoresUrlOnInput() {
            BackgroundAttributes result = objectMapper.readValue(
                    "{\"type\":\"IMAGE\",\"key\":\"" + IMAGE_KEY
                            + "\",\"opacity\":0.8,\"url\":\"https://attacker.example/x.png\"}",
                    BackgroundAttributes.class);

            assertThat(result).isEqualTo(new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null));
        }

        @Test
        @DisplayName("url이 null이면 직렬화에서 빠진다 — DB JSON에 url:null이 저장되지 않는다")
        void omitsNullUrl() {
            String json = objectMapper.writeValueAsString(
                    new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null));

            assertThat(json).isEqualTo(
                    "{\"type\":\"IMAGE\",\"key\":\"" + IMAGE_KEY + "\",\"opacity\":0.8}");
        }

        @Test
        @DisplayName("withUrl로 주입한 url은 나가기만 하고 되읽으면 다시 null이다 — 응답 전용 필드")
        void urlIsOneWay() {
            BackgroundAttributes.Image withUrl = new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null)
                    .withUrl("https://bucket.s3.example/presigned");

            String json = objectMapper.writeValueAsString(withUrl);
            assertThat(json).contains("\"url\":\"https://bucket.s3.example/presigned\"");

            BackgroundAttributes reread = objectMapper.readValue(json, BackgroundAttributes.class);
            assertThat(reread).isEqualTo(new BackgroundAttributes.Image(IMAGE_KEY, 0.8, null));
        }
    }

    @Nested
    @DisplayName("미등록 타입")
    class UnregisteredType {

        @Test
        @DisplayName("GRADIENT는 @JsonSubTypes에 없어 역직렬화가 실패한다")
        void rejectsGradient() {
            assertThatThrownBy(() -> objectMapper.readValue(
                    "{\"type\":\"GRADIENT\",\"value\":\"#FFF\"}", BackgroundAttributes.class))
                    .isInstanceOf(InvalidTypeIdException.class);
        }
    }

    @Nested
    @DisplayName("필수 값 거부")
    class RequiredFields {

        @Test
        @DisplayName("COLOR에 value가 없으면 역직렬화가 실패한다 — 없으면 value=null이 조용히 저장된다")
        void rejectsMissingValue() {
            assertThatThrownBy(() -> objectMapper.readValue(
                    "{\"type\":\"COLOR\"}", BackgroundAttributes.class))
                    .isInstanceOf(ValueInstantiationException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("IMAGE에 opacity가 없으면 역직렬화가 실패한다 — 원시형 double이면 0.0(완전 투명)이 된다")
        void rejectsMissingOpacity() {
            assertThatThrownBy(() -> objectMapper.readValue(
                    "{\"type\":\"IMAGE\",\"key\":\"" + IMAGE_KEY + "\"}", BackgroundAttributes.class))
                    .isInstanceOf(ValueInstantiationException.class)
                    .hasRootCauseInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("컴팩트 생성자는 빈 문자열도 거부한다")
        void rejectsBlankValues() {
            assertThatThrownBy(() -> new BackgroundAttributes.Color(" "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BackgroundAttributes.Image(" ", 0.8, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
