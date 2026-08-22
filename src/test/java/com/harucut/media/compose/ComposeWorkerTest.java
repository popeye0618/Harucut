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

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

// 워커의 계약이 비동기 전환으로 바뀌었다: 이제 Job을 끝내지 않는다.
// 선점하고 Lambda에 접수시키는 데까지가 전부고, DONE/FAILED는 ComposeResultConsumer가 찍는다.
// 그래서 이 테스트의 대부분은 "무엇을 하는가"가 아니라 "무엇을 하지 않는가"를 본다
@ExtendWith(MockitoExtension.class)
@DisplayName("ComposeWorker")
class ComposeWorkerTest {

    private static final Long JOB_ID = 5L;
    private static final List<String> SOURCE_KEYS = List.of(
            "uploads/u/1.jpg", "uploads/u/2.jpg", "uploads/u/3.jpg", "uploads/u/4.jpg");
    private static final String RESULT_KEY = "uploads/u/fourcuts/job-5.png";
    private static final String THUMB_KEY = "uploads/u/fourcuts/job-5-thumb.jpg";
    private static final Duration STALE_AFTER = Duration.ofMinutes(10);

    @Mock
    private ComposeExecutor composeExecutor;

    @Mock
    private ComposeService composeService;

    private ComposeWorker worker;

    @BeforeEach
    void setUp() {
        worker = new ComposeWorker(composeExecutor, composeService, STALE_AFTER);
    }

    @Test
    @DisplayName("선점한 뒤 실행기에 넘기고 끝난다 — 결과는 기록하지 않는다")
    void claimsThenHandsOffToExecutor() {
        given(composeService.claim(JOB_ID, STALE_AFTER)).willReturn(true);

        worker.handle(event());

        InOrder order = inOrder(composeExecutor, composeService);
        // 선점이 먼저다. 반대면 통지가 먼저 돌아와 아직 도장 안 찍힌 Job을 만난다
        order.verify(composeService).claim(JOB_ID, STALE_AFTER);
        order.verify(composeExecutor).execute(event());
        // 여기서 completeJob을 부르면 아직 그리지도 않은 Job이 DONE이 된다 —
        // 비동기 invoke는 "접수했다"는 뜻일 뿐이다
        then(composeService).should(never()).completeJob(any(), any(), any());
        then(composeService).should(never()).failJob(any(), anyString());
    }

    @Test
    @DisplayName("접수가 실패해도 FAILED로 적지 않는다 — PENDING으로 둬야 재실행이 줍는다")
    void acceptanceFailureLeavesJobPending() {
        given(composeService.claim(JOB_ID, STALE_AFTER)).willReturn(true);
        willThrow(new IllegalStateException("Lambda 접수 실패 (status=500)"))
                .given(composeExecutor).execute(any());

        // 예외가 밖으로 새면 @Async 스레드나 스케줄러 루프가 그걸 뒤집어쓴다
        assertThatCode(() -> worker.handle(event())).doesNotThrowAnyException();

        then(composeService).should(never()).failJob(any(), anyString());
        then(composeService).should(never()).completeJob(any(), any(), any());
    }

    @Test
    @DisplayName("선점에 실패하면 아무것도 하지 않는다 — 이벤트와 재실행이 같은 Job을 밀어 넣어도 한 번만 돈다")
    void skipsWhenAlreadyClaimed() {
        given(composeService.claim(JOB_ID, STALE_AFTER)).willReturn(false);

        worker.handle(event());

        then(composeExecutor).shouldHaveNoInteractions();
        then(composeService).should(never()).completeJob(any(), any(), any());
        then(composeService).should(never()).failJob(any(), anyString());
    }

    @Test
    @DisplayName("재실행도 이벤트와 같은 문을 지난다 — 선점을 거쳐 접수한다")
    void rerunTakesSamePath() {
        given(composeService.claim(JOB_ID, STALE_AFTER)).willReturn(true);

        worker.rerun(event());

        InOrder order = inOrder(composeExecutor, composeService);
        order.verify(composeService).claim(JOB_ID, STALE_AFTER);
        order.verify(composeExecutor).execute(event());
    }

    private static ComposeRequestedEvent event() {
        return new ComposeRequestedEvent(JOB_ID,
                new ComposeSpec(2000, 6000, new BackgroundAttributes.Color("#FFF"),
                        FrameType.CLASSIC.getLayout().slots(),
                        List.of(false, false, false, false), List.of()),
                SOURCE_KEYS, RESULT_KEY, THUMB_KEY);
    }
}
