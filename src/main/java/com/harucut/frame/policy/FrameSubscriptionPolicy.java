package com.harucut.frame.policy;

import java.time.LocalDateTime;

public interface FrameSubscriptionPolicy {

    // 현재 currentFrameCount개 보유 상태에서 하나 더 만들 수 있는지. 못 만들면 SUBS-003
    void assertFrameRetentionLimit(Long userId, int currentFrameCount);

    // 동시 보관 상한. null = 무제한
    Integer resolveFrameRetentionCap(Long userId);

    // 이보다 오래된 내역은 목록에서 숨긴다. null = 무제한
    LocalDateTime resolveHistoryCutoff(Long userId);

    // createdAt이 보관 기간 밖이면 SUBS-002
    void assertHistoryAccessible(Long userId, LocalDateTime createdAt);
}
