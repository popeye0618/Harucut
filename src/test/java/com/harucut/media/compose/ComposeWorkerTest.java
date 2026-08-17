package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.enums.FrameType;
import com.harucut.media.service.ComposeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("ComposeWorker")
class ComposeWorkerTest {

    private static final Long JOB_ID = 5L;
    private static final List<String> SOURCE_KEYS = List.of(
            "uploads/u/1.jpg", "uploads/u/2.jpg", "uploads/u/3.jpg", "uploads/u/4.jpg");
    private static final String RESULT_KEY = "uploads/u/fourcuts/job-5.png";
    private static final String THUMB_KEY = "uploads/u/fourcuts/job-5-thumb.jpg";

    @Mock
    private ComposeExecutor composeExecutor;

    @Mock
    private ComposeService composeService;

    private ComposeWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ComposeWorker(composeExecutor, composeService);
    }

    @Test
    @DisplayName("실행이 끝난 뒤에 완료를 기록한다 — 순서 보장")
    void executesThenCompletes() {
        worker.handle(event());

        InOrder order = inOrder(composeExecutor, composeService);
        order.verify(composeExecutor).execute(event().spec(), SOURCE_KEYS, RESULT_KEY, THUMB_KEY);
        order.verify(composeService).completeJob(JOB_ID, RESULT_KEY, THUMB_KEY);
        then(composeService).should(never()).failJob(any(), anyString());
    }

    @Test
    @DisplayName("실행이 죽으면 실패로 기록하고 완료는 부르지 않는다")
    void executionFailureRecorded() {
        willThrow(new IllegalStateException("S3 다운로드 실패"))
                .given(composeExecutor).execute(any(), any(), any(), any());

        worker.handle(event());

        then(composeService).should().failJob(JOB_ID, "S3 다운로드 실패");
        then(composeService).should(never()).completeJob(any(), any(), any());
    }

    @Test
    @DisplayName("완료 기록이 죽어도 실패 기록을 시도한다")
    void completionFailureFallsBackToFail() {
        willThrow(new IllegalStateException("DB 오류"))
                .given(composeService).completeJob(JOB_ID, RESULT_KEY, THUMB_KEY);

        worker.handle(event());

        then(composeService).should().failJob(JOB_ID, "DB 오류");
    }

    @Test
    @DisplayName("실패 기록마저 죽어도 워커는 터지지 않는다 — 로그만 남기고 삼킨다")
    void failRecordingFailureSwallowed() {
        willThrow(new IllegalStateException("실행 실패"))
                .given(composeExecutor).execute(any(), any(), any(), any());
        willThrow(new IllegalStateException("DB도 죽음"))
                .given(composeService).failJob(any(), anyString());

        assertThatCode(() -> worker.handle(event())).doesNotThrowAnyException();
    }

    private static ComposeRequestedEvent event() {
        return new ComposeRequestedEvent(JOB_ID,
                new ComposeSpec(2000, 6000, new BackgroundAttributes.Color("#FFF"),
                        FrameType.CLASSIC.getLayout().slots(),
                        List.of(false, false, false, false), List.of()),
                SOURCE_KEYS, RESULT_KEY, THUMB_KEY);
    }
}
