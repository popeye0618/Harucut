package com.harucut.frame.enums;

import com.harucut.frame.layout.FrameLayout;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum FrameType {
    CLASSIC(new FrameLayout(2000, 6000)),
    WIDE(new FrameLayout(6000, 4000)),
    GRID(new FrameLayout(4000, 6000)),
    POLAROID(new FrameLayout(4000, 6000));

    private final FrameLayout layout;
}
