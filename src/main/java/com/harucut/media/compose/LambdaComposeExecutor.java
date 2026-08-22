package com.harucut.media.compose;

import com.harucut.storage.config.AwsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;
import software.amazon.awssdk.services.lambda.model.InvokeResponse;
import tools.jackson.databind.ObjectMapper;

// 실행기 — 그리지 않고 Lambda를 동기 호출한다. 호출의 응답이 곧 완료 통지라서
// 콜백 엔드포인트·큐가 필요 없다 (decisions.md 네컷 합성 결정).
// 실패(FunctionError)는 예외로 바꿔 워커가 Job을 FAILED로 기록하게 한다
@Slf4j
@Component
public class LambdaComposeExecutor implements ComposeExecutor {

    private final LambdaClient lambdaClient;
    private final ObjectMapper objectMapper;
    private final String functionName;
    private final String bucket;

    public LambdaComposeExecutor(LambdaClient lambdaClient, ObjectMapper objectMapper,
                                 AwsProperties awsProperties) {
        this.lambdaClient = lambdaClient;
        this.objectMapper = objectMapper;
        this.bucket = awsProperties.s3().bucket();
        if (awsProperties.lambda() == null || awsProperties.lambda().composeFunction() == null
                || awsProperties.lambda().composeFunction().isBlank()) {
            // 스위치는 켰는데 함수 이름이 없으면 기동에서 죽는다 — 첫 합성 요청에서 죽는 것보다 낫다
            throw new IllegalStateException(
                    "compose.executor=lambda인데 cloud.aws.lambda.compose-function이 비어 있다");
        }
        this.functionName = awsProperties.lambda().composeFunction();
    }

    @Override
    public void execute(ComposeRequestedEvent event) {
        String payload = objectMapper.writeValueAsString(new ComposeLambdaPayload(
                bucket, event.jobId(), event.spec(), event.sourceKeys(),
                event.resultKey(), event.thumbnailKey()));

        InvokeResponse response = lambdaClient.invoke(InvokeRequest.builder()
                .functionName(functionName)
                .invocationType(InvocationType.EVENT)
                .payload(SdkBytes.fromUtf8String(payload))
                .build());

        // 비동기 접수는 202다. 200을 기대하면 성공한 호출이 전부 예외가 된다
        if (response.statusCode() != 202) {
            throw new IllegalStateException("Lambda 접수 실패 (status=" + response.statusCode()
                    + "): " + excerpt(response.payload()));
        }
    }

    private String excerpt(SdkBytes payload) {
        if (payload == null) {
            return "";
        }
        String text = payload.asUtf8String();
        return text.length() > 300 ? text.substring(0, 300) : text;
    }
}
