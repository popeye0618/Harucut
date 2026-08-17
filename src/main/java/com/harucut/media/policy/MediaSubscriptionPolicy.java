package com.harucut.media.policy;

import java.time.LocalDateTime;

public interface MediaSubscriptionPolicy {

    LocalDateTime resolveHistoryCutoff(Long userId);

    void assertHistoryAccessible(Long userId, LocalDateTime createdAt);
}
