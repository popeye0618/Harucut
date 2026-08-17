package com.harucut.compose.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.harucut.media.compose.ComposeLambdaPayload;
import com.harucut.media.compose.FourcutRenderer;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Lambda 진입점: payload → S3 다운로드 → 렌더 → S3 업로드.
// Lambda의 자동 역직렬화(자체 내장 잭슨)를 안 쓰고 스트림을 직접 파싱한다 —
// 다형성 배경(@JsonTypeInfo)은 서버와 같은 잭슨(tools.jackson)으로 읽어야 어긋나지 않는다.
// 필드들은 워밍된 실행 환경에서 재사용된다 — 호출마다 새로 만들지 않는다
public class ComposeHandler implements RequestStreamHandler {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final S3Client s3Client;
    private final FourcutRenderer renderer = new FourcutRenderer();

    public ComposeHandler() {
        // Lambda 런타임이 부르는 생성자 — region·자격증명은 함수 실행 역할(role)에서 온다
        this(S3Client.create());
    }

    ComposeHandler(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context)
            throws IOException {
        ComposeLambdaPayload payload = MAPPER.readValue(input, ComposeLambdaPayload.class);

        List<byte[]> sources = payload.sourceKeys().stream()
                .map(key -> download(payload.bucket(), key))
                .toList();

        // 자산 목록은 스펙이 소유한다 — 서버의 InProcessComposeExecutor와 같은 계산
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String key : payload.spec().referencedAssetKeys()) {
            assets.put(key, download(payload.bucket(), key));
        }

        // 썸네일 업로드 연결은 후속 단계 — 지금은 원본만 올린다
        byte[] png = renderer.render(payload.spec(), sources, assets).fullPng();

        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(payload.bucket()).key(payload.resultKey())
                        .contentType("image/png").build(),
                RequestBody.fromBytes(png));

        output.write("{\"ok\":true}".getBytes(StandardCharsets.UTF_8));
    }

    private byte[] download(String bucket, String key) {
        return s3Client.getObjectAsBytes(
                GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();
    }
}
