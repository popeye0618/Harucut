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
    private static final Long JOB_ID = 5L;

    @Mock
    private LambdaClient lambdaClient;

    @Test
    @DisplayName("비동기 호출로 함수 이름·버킷·jobId·스펙이 담긴 payload를 보낸다")
    void invokesWithSerializedPayload() {
        LambdaComposeExecutor executor = executor();
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder().statusCode(202).build());

        executor.execute(event());

        ArgumentCaptor<InvokeRequest> captor = ArgumentCaptor.captor();
        then(lambdaClient).should().invoke(captor.capture());
        InvokeRequest request = captor.getValue();
        assertThat(request.functionName()).isEqualTo("harucut-compose");
        assertThat(request.invocationType()).isEqualTo(InvocationType.EVENT);
        ComposeLambdaPayload payload = MAPPER.readValue(
                request.payload().asUtf8String(), ComposeLambdaPayload.class);
        assertThat(payload.bucket()).isEqualTo("harucut-test");
        assertThat(payload.jobId()).isEqualTo(JOB_ID);
        assertThat(payload.sourceKeys()).containsExactlyElementsOf(SOURCE_KEYS);
        assertThat(payload.resultKey()).isEqualTo(RESULT_KEY);
        assertThat(payload.thumbnailKey()).isEqualTo(THUMB_KEY);
        assertThat(payload.spec()).isEqualTo(spec());
    }

    @Test
    @DisplayName("접수가 202가 아니면 예외로 바꾼다 — 워커가 PENDING으로 두고 재실행에 맡긴다")
    void nonAcceptedStatusBecomesException() {
        LambdaComposeExecutor executor = executor();
        // 비동기 호출은 함수가 돌기 전에 응답이 온다. 그래서 여기서 알 수 있는 실패는
        // "함수가 죽었다"가 아니라 "접수를 못 시켰다"뿐이다 — 실행 실패는 Destination으로 온다
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder()
                        .statusCode(500)
                        .payload(SdkBytes.fromUtf8String("{\"Message\":\"Service Unavailable\"}"))
                        .build());

        assertThatThrownBy(() -> executor.execute(event()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("500");
    }

    @Test
    @DisplayName("접수 성공(202)이면 조용히 끝난다 — 이 응답은 완료 통지가 아니다")
    void successReturnsQuietly() {
        LambdaComposeExecutor executor = executor();
        given(lambdaClient.invoke(any(InvokeRequest.class)))
                .willReturn(InvokeResponse.builder().statusCode(202).build());

        assertThatCode(() -> executor.execute(event()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("함수 이름이 없으면 기동에서 죽는다 — 첫 요청에서 죽는 것보다 낫다")
    void missingFunctionNameFailsAtStartup() {
        AwsProperties noFunction = new AwsProperties("ap-northeast-2",
                new AwsProperties.S3("harucut-test"), null, null);

        assertThatThrownBy(() -> new LambdaComposeExecutor(lambdaClient, MAPPER, noFunction))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── fixtures ──────────────────────────────

    private LambdaComposeExecutor executor() {
        return new LambdaComposeExecutor(lambdaClient, MAPPER, new AwsProperties(
                "ap-northeast-2", new AwsProperties.S3("harucut-test"),
                new AwsProperties.Lambda("harucut-compose"), null));
    }

    private static ComposeRequestedEvent event() {
        return new ComposeRequestedEvent(JOB_ID, spec(), SOURCE_KEYS, RESULT_KEY, THUMB_KEY);
    }

    private static ComposeSpec spec() {
        return new ComposeSpec(2000, 6000,
                new BackgroundAttributes.Color("#FFE4E1"),
                FrameType.CLASSIC.getLayout().slots(),
                List.of(false, false, false, false),
                List.of());
    }
}
