package com.harucut.subscription.port;

import org.springframework.stereotype.Component;

@Component
public class ZeroFrameCountAdapter implements FrameCountPort {

    @Override
    public int countByUserId(Long userId) {
        return 0;
    }
}
