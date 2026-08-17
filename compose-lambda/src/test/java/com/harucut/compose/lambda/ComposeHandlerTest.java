package com.harucut.compose.lambda;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.layout.FrameLayout.Slot;
import com.harucut.media.compose.ComposeLambdaPayload;
import com.harucut.media.compose.ComposeSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComposeHandler")
class ComposeHandlerTest {

    // 서버(LambdaComposeExecutor)와 같은 잭슨 — payload 계약의 wire 검증
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final String BUCKET = "harucut-test";
    private static final String RESULT_KEY = "uploads/users/abc/fourcuts/job-5.png";
    private static final List<String> SOURCE_KEYS = List.of(
            "uploads/u/1.jpg", "uploads/u/2.jpg", "uploads/u/3.jpg", "uploads/u/4.jpg");

    @Mock
    private S3Client s3Client;

    @Test
    @DisplayName("서버 잭슨이 만든 payload(다형성 IMAGE 배경 포함)를 읽어 그리고 결과를 올린다")
    void roundTripWithPolymorphicBackground() throws IOException {
        ComposeSpec spec = new ComposeSpec(40, 40,
                new BackgroundAttributes.Image("uploads/u/bg.png", 0.8, null),
                fourSlots(), List.of(false, false, false, false), List.of());
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), solidPng(10, 10, Color.RED)));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        new ComposeHandler(s3Client).handleRequest(payloadStream(spec), output, null);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.captor();
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.captor();
        then(s3Client).should().putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertThat(requestCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(requestCaptor.getValue().key()).isEqualTo(RESULT_KEY);
        assertThat(requestCaptor.getValue().contentType()).isEqualTo("image/png");
        BufferedImage result = ImageIO.read(
                bodyCaptor.getValue().contentStreamProvider().newStream());
        assertThat(result.getWidth()).isEqualTo(40);
        assertThat(result.getHeight()).isEqualTo(40);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo("{\"ok\":true}");
    }

    @Test
    @DisplayName("원본 4장과 스펙이 참조하는 자산(배경·레이어)을 전부 내려받는다")
    void downloadsSourcesAndAssets() throws IOException {
        ComposeSpec spec = new ComposeSpec(40, 40,
                new BackgroundAttributes.Image("uploads/u/bg.png", 1.0, null),
                fourSlots(), List.of(false, false, false, false),
                List.of(new ComposeSpec.Layer("uploads/u/sticker.png",
                        0, 0, 10, 10, 1.0, 0, 1, 1.0)));
        given(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .willReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(), solidPng(10, 10, Color.RED)));

        new ComposeHandler(s3Client).handleRequest(
                payloadStream(spec), new ByteArrayOutputStream(), null);

        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.captor();
        then(s3Client).should(times(6)).getObjectAsBytes(captor.capture());
        assertThat(captor.getAllValues()).extracting(GetObjectRequest::key)
                .containsExactly("uploads/u/1.jpg", "uploads/u/2.jpg", "uploads/u/3.jpg",
                        "uploads/u/4.jpg", "uploads/u/bg.png", "uploads/u/sticker.png");
        assertThat(captor.getAllValues()).extracting(GetObjectRequest::bucket)
                .allMatch(BUCKET::equals);
    }

    // ── fixtures ──────────────────────────────

    private static ByteArrayInputStream payloadStream(ComposeSpec spec) {
        String json = MAPPER.writeValueAsString(
                new ComposeLambdaPayload(BUCKET, spec, SOURCE_KEYS, RESULT_KEY));
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private static List<Slot> fourSlots() {
        return List.of(new Slot(0, 0, 10, 10), new Slot(10, 0, 10, 10),
                new Slot(0, 10, 10, 10), new Slot(10, 10, 10, 10));
    }

    private static byte[] solidPng(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
