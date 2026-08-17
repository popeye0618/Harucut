package com.harucut.media.compose;

import com.harucut.common.exception.BusinessException;
import com.harucut.common.exception.GlobalErrorCode;
import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.entity.FrameComponent;
import com.harucut.frame.layout.FrameLayout;
import com.harucut.storage.util.S3Keys;
import org.springframework.stereotype.Component;

import java.util.List;

// 요청 시점의 Frame을 자기 완결 렌더 스펙으로 바꾼다 — Job에 스냅샷으로 저장될 값이다.
// 여기가 "이 프레임으로 합성이 가능한가"의 관문이기도 하다: 합성기는 S3에서만 가져오므로
// S3에 없는 참조(안 구운 텍스트, 외부 URL 자산)는 여기서 400으로 끊는다
@Component
public class ComposeSpecAssembler {

    public ComposeSpec assemble(Frame frame) {
        FrameLayout layout = frame.getFrameType().getLayout();
        return new ComposeSpec(
                layout.canvasWidth(), layout.canvasHeight(),
                composableBackground(frame.getBackground()),
                layout.slots(),
                frame.getCellCutouts(),
                frame.getComponents().stream().map(this::toLayer).toList());
    }

    private BackgroundAttributes composableBackground(BackgroundAttributes background) {
        if (background instanceof BackgroundAttributes.Image image
                && !S3Keys.isManagedKey(image.key())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE,
                    "S3에 없는 배경 이미지는 합성할 수 없다.");
        }
        return background;
    }

    private ComposeSpec.Layer toLayer(FrameComponent component) {
        String source = switch (component.getType()) {
            case PHOTO, STICKER -> component.getSource();
            case TEXT -> component.getRenderedKey();
        };
        if (source == null || source.isBlank()) {
            // 1단계에서 미뤄둔 renderedKey 검증의 자리 — 안 구운 텍스트는 여기서 걸린다
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE,
                    "구운 텍스트 이미지(renderedKey)가 없는 프레임은 합성할 수 없다.");
        }
        if (!S3Keys.isManagedKey(source)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE,
                    "S3에 없는 자산은 합성할 수 없다: " + source);
        }
        return new ComposeSpec.Layer(
                source,
                component.getX(), component.getY(),
                component.getWidth() == null ? 0.0 : component.getWidth(),
                component.getHeight() == null ? 0.0 : component.getHeight(),
                component.getScale() == null ? 1.0 : component.getScale(),
                component.getRotation(),
                component.getZIndex(),
                opacityOf(component));
    }

    // 프론트 drawThemeOverlay와 같은 읽기: styleJson.opacity가 숫자면 0~1로 잘라 쓰고, 아니면 1
    private double opacityOf(FrameComponent component) {
        Object raw = component.getStyle().get("opacity");
        if (raw instanceof Number number) {
            return Math.min(1.0, Math.max(0.0, number.doubleValue()));
        }
        return 1.0;
    }
}
