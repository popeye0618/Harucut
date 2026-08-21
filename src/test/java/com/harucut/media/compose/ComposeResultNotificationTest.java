package com.harucut.media.compose;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// wire 검증이다. 아래 JSON은 실제 Destination 통지에서 그대로 가져온 모양이고,
// 우리가 안 쓰는 필드(version·timestamp·requestId·functionArn·responseContext·stackTrace)를
// 일부러 전부 남겨뒀다 — 그것들이 있어도 파싱이 되어야 이 설계가 성립한다.
//
// @JsonTest 로 스프링이 만든 ObjectMapper 를 그대로 쓴다. 소비자가 주입받는 것과 같은 매퍼여야
// 의미가 있다 — JsonMapper.builder().build() 로 직접 만들면 모르는 필드 관용이 보장되지 않는다
@JsonTest
@ActiveProfiles("test")
@DisplayName("ComposeResultNotification")
class ComposeResultNotificationTest {

    private static final Long JOB_ID = 231L;
    private static final String RESULT_KEY = "uploads/users/abc/fourcuts/job-231.png";
    private static final String THUMB_KEY = "uploads/users/abc/fourcuts/job-231-thumb.jpg";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("성공 통지에서 jobId와 결과 key를 꺼낸다 — responsePayload가 {\"ok\":true}여도 깨지지 않는다")
    void parsesSuccessNotification() {
        ComposeResultNotification notification = objectMapper.readValue(
                notification("Success", 1, """
                        "responseContext": { "statusCode": 200, "executedVersion": "$LATEST" },
                        "responsePayload": { "ok": true }"""),
                ComposeResultNotification.class);

        assertThat(notification.condition()).isEqualTo("Success");
        assertThat(notification.jobId()).isEqualTo(JOB_ID);
        assertThat(notification.requestPayload().resultKey()).isEqualTo(RESULT_KEY);
        assertThat(notification.requestPayload().thumbnailKey()).isEqualTo(THUMB_KEY);
        // 스펙도 통째로 돌아온다 — 다형성 배경(@JsonTypeInfo)이 통지를 왕복해도 살아남는지가 핵심
        assertThat(notification.requestPayload().spec().canvasHeight()).isEqualTo(1200);
    }

    @Test
    @DisplayName("영구 실패 통지에서 사유를 조립한다 — stackTrace는 버린다")
    void parsesRetriesExhaustedNotification() {
        ComposeResultNotification notification = objectMapper.readValue(
                notification("RetriesExhausted", 3, """
                        "responseContext": { "statusCode": 200, "executedVersion": "$LATEST",
                                             "functionError": "Unhandled" },
                        "responsePayload": {
                          "errorMessage": "The specified key does not exist. (Service: S3, Status Code: 404)",
                          "errorType": "software.amazon.awssdk.services.s3.model.NoSuchKeyException",
                          "stackTrace": [
                            "software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handleErrorResponse(CombinedResponseHandler.java:125)",
                            "software.amazon.awssdk.core.internal.http.CombinedResponseHandler.handleResponse(CombinedResponseHandler.java:82)"
                          ]
                        }"""),
                ComposeResultNotification.class);

        assertThat(notification.condition()).isEqualTo("RetriesExhausted");
        assertThat(notification.jobId()).isEqualTo(JOB_ID);
        assertThat(notification.failureReason())
                .startsWith("software.amazon.awssdk.services.s3.model.NoSuchKeyException: ")
                .contains("The specified key does not exist.")
                // failure_reason 은 255자에서 잘린다(ComposeJob.fail) — 스택이 섞이면 원인이 잘려 나간다
                .doesNotContain("CombinedResponseHandler");
    }

    @Test
    @DisplayName("responsePayload 없는 통지도 읽는다 — EventAgeExceeded는 함수가 돌지도 않았다")
    void parsesNotificationWithoutResponsePayload() {
        ComposeResultNotification notification = objectMapper.readValue(
                notification("EventAgeExceeded", 3, """
                        "responseContext": null"""),
                ComposeResultNotification.class);

        assertThat(notification.condition()).isEqualTo("EventAgeExceeded");
        assertThat(notification.jobId()).isEqualTo(JOB_ID);
        // 소비자가 이 값을 부르지는 않지만(PENDING 유지 분기), NPE로 죽으면 메시지가 DLQ로 샌다
        assertThatCode(notification::failureReason).doesNotThrowAnyException();
        assertThat(notification.failureReason()).isEqualTo("EventAgeExceeded");
    }

    // ── fixtures ──────────────────────────────

    // requestPayload 는 우리가 invoke 에 실어 보낸 바이트가 그대로 돌아온 것이다 —
    // AWS 가 다시 직렬화하지 않는다
    private static String notification(String condition, int invokeCount, String tail) {
        return """
                {
                  "version": "1.0",
                  "timestamp": "2026-08-21T07:11:02.082Z",
                  "requestContext": {
                    "requestId": "b57694bf-8ef0-42e4-aac7-c6997d00c6c2",
                    "functionArn": "arn:aws:lambda:ap-northeast-2:923246546766:function:harucut-compose:$LATEST",
                    "condition": "%s",
                    "approximateInvokeCount": %d
                  },
                  "requestPayload": {
                    "bucket": "harucuts3",
                    "jobId": 231,
                    "spec": {
                      "canvasWidth": 400,
                      "canvasHeight": 1200,
                      "background": { "type": "COLOR", "value": "#FFFFFF" },
                      "slots": [
                        { "x": 0, "y": 0,   "width": 400, "height": 300 },
                        { "x": 0, "y": 300, "width": 400, "height": 300 },
                        { "x": 0, "y": 600, "width": 400, "height": 300 },
                        { "x": 0, "y": 900, "width": 400, "height": 300 }
                      ],
                      "cellCutouts": [false, false, false, false],
                      "layers": []
                    },
                    "sourceKeys": [
                      "uploads/users/abc/1.jpg", "uploads/users/abc/2.jpg",
                      "uploads/users/abc/3.jpg", "uploads/users/abc/4.jpg"
                    ],
                    "resultKey": "%s",
                    "thumbnailKey": "%s"
                  },
                  %s
                }
                """.formatted(condition, invokeCount, RESULT_KEY, THUMB_KEY, tail);
    }
}
