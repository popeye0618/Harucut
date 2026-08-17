package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.enums.FrameType;
import com.harucut.storage.config.AwsProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("LambdaComposeExecutor")
class LambdaComposeExecutorTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final List<String> SOURCE_KEYS = List.of(
            "uploads/u/1.jpg", "uploads/u/2.jpg", "uploads/u/3.jpg", "uploads/u/4.jpg");
    private static final String RESULT_KEY = "uploads/u/fourcuts/job-5.png";
    private static final String THUMB_KEY = "uploads/u/fourcuts/job-5-thumb.jpg";

    @Mock
    private LambdaClient lambdaClient;

    @Test
    @DisplayName("동기 호출로 함수 이름·버킷·스펙이 담긴 payload를 보낸다")
    void invokesWithSerializedPayload() {
        LambdaComposeExecutor executor = executor();
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder().statusCode(200).build());

        executor.execute(spec(), SOURCE_KEYS, RESULT_KEY, THUMB_KEY);

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.captor();
        then(lambdaClient).should().invoke(captor.capture());
        InvokeRequest request = captor.getValue();
        assertThat(request.functionName()).isEqualTo("harucut-compose");
        assertThat(request.invocationType()).isEqualTo(InvocationType.REQUEST_RESPONSE);
        ComposeLambdaPayload payload = MAPPER.readValue(
                request.payload().asUtf8String(), ComposeLambdaPayload.class);
        assertThat(payload.bucket()).isEqualTo("harucut-test");
        assertThat(payload.sourceKeys()).containsExactlyElementsOf(SOURCE_KEYS);
        assertThat(payload.resultKey()).isEqualTo(RESULT_KEY);
        assertThat(payload.thumbnailKey()).isEqualTo(THUMB_KEY);
        assertThat(payload.spec()).isEqualTo(spec());
    }

    @Test
    @DisplayName("FunctionError가 오면 예외로 바꾼다 — 워커가 FAILED로 기록하게")
    void functionErrorBecomesException() {
        LambdaComposeExecutor executor = executor();
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder()
                        .statusCode(200)
                        .functionError("Unhandled")
                        .payload(SdkBytes.fromUtf8String("{\"errorMessage\":\"OOM\"}"))
                        .build());

        assertThatThrownBy(() -> executor.execute(spec(), SOURCE_KEYS, RESULT_KEY, THUMB_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unhandled");
    }

    @Test
    @DisplayName("정상 응답(200, 에러 없음)이면 조용히 끝난다")
    void successReturnsQuietly() {
        LambdaComposeExecutor executor = executor();
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder().statusCode(200).build());

        assertThatCode(() -> executor.execute(spec(), SOURCE_KEYS, RESULT_KEY, THUMB_KEY))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("스위치는 켰는데 함수 이름이 없으면 기동에서 죽는다 — 첫 요청에서 죽는 것보다 낫다")
    void missingFunctionNameFailsAtStartup() {
        AwsProperties noFunction = new AwsProperties("ap-northeast-2",
                new AwsProperties.S3("harucut-test"), null);

        assertThatThrownBy(() -> new LambdaComposeExecutor(lambdaClient, MAPPER, noFunction))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── fixtures ──────────────────────────────

    private LambdaComposeExecutor executor() {
        return new LambdaComposeExecutor(lambdaClient, MAPPER, new AwsProperties(
                "ap-northeast-2", new AwsProperties.S3("harucut-test"),
                new AwsProperties.Lambda("harucut-compose")));
    }

    private static ComposeSpec spec() {
        return new ComposeSpec(2000, 6000,
                new BackgroundAttributes.Color("#FFE4E1"),
                FrameType.CLASSIC.getLayout().slots(),
                List.of(false, false, false, false),
                List.of());
    }
}
