package com.harucut.media.service;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.service.FrameService;
import com.harucut.media.compose.ComposeRequestedEvent;
import com.harucut.media.compose.ComposeSpec;
import com.harucut.media.compose.ComposeSpecAssembler;
import com.harucut.media.dto.ComposeJobResponse;
import com.harucut.media.dto.ComposeRequest;
import com.harucut.media.entity.ComposeJob;
import com.harucut.media.entity.UserMedia;
import com.harucut.media.enums.ComposeStatus;
import com.harucut.media.repository.ComposeJobRepository;
import com.harucut.media.repository.UserMediaRepository;
import com.harucut.media.util.DisplayNames;
import com.harucut.storage.service.S3Deleter;
import com.harucut.storage.util.S3Keys;
import com.harucut.user.entity.User;
import com.harucut.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class ComposeService {

    private final UserRepository userRepository;
    private final ComposeJobRepository composeJobRepository;
    private final UserMediaRepository userMediaRepository;
    private final FrameService frameService;
    private final ComposeSpecAssembler composeSpecAssembler;
    private final S3Deleter s3Deleter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ComposeJobResponse requestCompose(String publicId, ComposeRequest request) {
        User user = getUser(publicId);

        // 멱등 재생: 더블클릭·재시도는 기존 Job을 그대로 받는다 — 새 실행 없음.
        // 정확히 동시에 온 중복은 (user, key) unique가 막는다 (한쪽은 실패하지만 데이터는 안전)
        Optional<ComposeJob> existing =
                composeJobRepository.findByUserAndIdempotencyKey(user, request.idempotencyKey());
        if (existing.isPresent()) {
            return ComposeJobResponse.from(existing.get());
        }

        // 원본은 정규화된 key로 저장하고, 검사도 그 값으로 한다 — URL로 감싼 남의 key 차단
        List<String> sourceKeys = request.sourceKeys().stream()
                .map(S3Keys::normalizeToKey)
                .toList();
        validateSourceOwnership(publicId, sourceKeys);

        Frame frame = frameService.getComposableFrame(user, request.frameId());
        ComposeSpec spec = composeSpecAssembler.assemble(frame);

        ComposeJob job = composeJobRepository.save(ComposeJob.create(
                user, frame.getId(), request.idempotencyKey(), sourceKeys, spec));

        // 실행은 커밋 후에 시작된다(AFTER_COMMIT 리스너) — 커밋 전에 실행 스레드가 뜨면
        // 아직 안 보이는 Job을 상대로 결과를 기록하려다 실패한다
        eventPublisher.publishEvent(new ComposeRequestedEvent(
                job.getId(), spec, job.sourceKeys(),
                resultKeyFor(publicId, job.getId()), thumbnailKeyFor(publicId, job.getId())));
        return ComposeJobResponse.from(job);
    }

    @Transactional(readOnly = true)
    public ComposeJobResponse getJob(String publicId, Long jobId) {
        User user = getUser(publicId);
        ComposeJob job = composeJobRepository.findByIdAndUser(jobId, user)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        return ComposeJobResponse.from(job);
    }

    // ── 워커가 실행 결과를 기록할 때 부르는 전이 — 각자 자기 트랜잭션에서 돈다 ──

    public void completeJob(Long jobId, String resultKey, String thumbnailKey) {
        ComposeJob job = composeJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("합성 Job이 사라졌다: " + jobId));
        if (job.getStatus() != ComposeStatus.PENDING) {
            return;   // 재실행 경합 — 먼저 끝난 쪽이 이미 기록했다
        }

        UserMedia media = userMediaRepository.save(UserMedia.of(job.getUser(), resultKey,
                thumbnailKey, DisplayNames.resolve(null, resultKey, LocalDateTime.now(clock))));
        job.complete(resultKey, media.getId());
        // 원본은 성공 시 삭제 — 결과만 보관함에 남는다 (decisions.md 네컷 합성 결정)
        s3Deleter.deleteAfterCommit(job.sourceKeys());
    }

    public void failJob(Long jobId, String reason) {
        composeJobRepository.findById(jobId).ifPresent(job -> job.fail(reason));
    }

    public boolean claim(Long jobId, Duration staleAfter) {
        LocalDateTime now = LocalDateTime.now(clock);
        return composeJobRepository.claim(jobId, now, now.minus(staleAfter)) == 1;
    }

    // 재실행 대상을 실행 payload 로 바꿔 내보낸다. 엔티티를 밖으로 내보내지 않는 이유는
    // 워커가 트랜잭션 밖에서 돌기 때문이다 — 지연 로딩이 거기서 터진다
    @Transactional(readOnly = true)
    public List<ComposeRequestedEvent> findStalled(Duration staleAfter, int limit) {
        LocalDateTime staleBefore = LocalDateTime.now(clock).minus(staleAfter);
        return composeJobRepository.findStalled(staleBefore, PageRequest.of(0, limit)).stream()
                .map(job -> {
                    String publicId = job.getUser().getPublicId();
                    return new ComposeRequestedEvent(job.getId(), job.getSpec(), job.sourceKeys(),
                            resultKeyFor(publicId, job.getId()),
                            thumbnailKeyFor(publicId, job.getId()));
                })
                .toList();
    }

    // Job당 결정적 결과 key — 재실행이 겹쳐도 같은 객체를 덮어쓰므로 고아 파일이 안 생기고,
    // UserMedia의 s3Key unique가 중복 행의 최종 방어선이 된다
    static String resultKeyFor(String publicId, Long jobId) {
        return S3Keys.userRoot(publicId) + "fourcuts/job-" + jobId + ".png";
    }

    // 썸네일도 같은 원칙의 결정적 key — 원본 옆에서 함께 살고 함께 지워진다
    static String thumbnailKeyFor(String publicId, Long jobId) {
        return S3Keys.userRoot(publicId) + "fourcuts/job-" + jobId + "-thumb.jpg";
    }

    // 남의 원본으로 합성 못 한다 — 프로필 이미지와 같은 규칙: 정규화된 key의 내 prefix 검사, 403
    private void validateSourceOwnership(String publicId, List<String> sourceKeys) {
        String root = S3Keys.userRoot(publicId);
        for (String sourceKey : sourceKeys) {
            if (!sourceKey.startsWith(root)) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN);
            }
        }
    }

    private User getUser(String publicId) {
        return userRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
    }
}
