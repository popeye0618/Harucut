package com.harucut.frame.service;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.frame.dto.FrameCreateRequest;
import com.harucut.frame.dto.FrameResponse;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.entity.FrameComponent;
import com.harucut.frame.enums.ComponentType;
import com.harucut.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// 사용자 프레임 서비스와 관리자 시스템 프레임 서비스가 공통으로 쓰는
// 요청→엔티티 조립, 엔티티→응답 변환기. 두 서비스는 서로가 아닌 이것에 의존한다
@Component
@RequiredArgsConstructor
public class FrameComponentAssembler {

    private final FrameAssetManager frameAssetManager;

    // 요청 → 엔티티. 저장 직전에 순수 key로 정규화된다 — PHOTO의 source, TEXT의 renderedKey
    public List<FrameComponent> createComponents(List<FrameCreateRequest.ComponentRequest> requests) {
        if (requests == null) {
            return List.of();
        }
        return requests.stream()
                .map(dto -> FrameComponent.builder()
                        .source(frameAssetManager.normalizeSource(dto.type(), dto.source()))
                        .type(dto.type())
                        .x(dto.x()).y(dto.y())
                        .width(dto.width()).height(dto.height()).scale(dto.scale())
                        .rotation(dto.rotation())
                        .zIndex(dto.zIndex())
                        .renderedKey(renderedKeyFor(dto))
                        .style(dto.styleJson())
                        .build())
                .toList();
    }

    // 구운 텍스트 key는 TEXT에만 의미가 있다 — 다른 타입이 보내면 버려서 쓰레기 저장을 막는다.
    // TEXT라도 선택 값: 없으면 null로 두고, 있어야 하는지는 합성 API가 검증한다
    private String renderedKeyFor(FrameCreateRequest.ComponentRequest dto) {
        if (dto.type() != ComponentType.TEXT
                || dto.renderedKey() == null || dto.renderedKey().isBlank()) {
            return null;
        }
        return frameAssetManager.normalizeImageKey(dto.renderedKey());
    }

    // 수정/삭제 시 S3 삭제 후보 수집 — 컴포넌트에서 우리 버킷 소유는
    // PHOTO의 source와 TEXT의 renderedKey 둘이다 (STICKER=정적 경로, TEXT source=본문)
    public List<String> extractAssetKeys(List<FrameComponent> components) {
        List<String> keys = new ArrayList<>();
        for (FrameComponent component : components) {
            if (component.getType() == ComponentType.PHOTO) {
                keys.add(component.getSource());
            }
            if (component.getRenderedKey() != null) {
                keys.add(component.getRenderedKey());
            }
        }
        return keys;
    }

    // 저장 직전 배경 정규화 — IMAGE key의 URL 흔적을 지우고, 응답 전용 url은 확실히 비운다
    public BackgroundAttributes normalizeBackground(BackgroundAttributes background) {
        return switch (background) {
            case BackgroundAttributes.Image image -> new BackgroundAttributes.Image(
                    frameAssetManager.normalizeImageKey(image.key()), image.opacity(), null);
            case BackgroundAttributes.Color color -> color;
        };
    }

    // 수정/삭제 시 S3 삭제 후보 — COLOR에는 지울 파일이 없다
    public String extractBackgroundKey(BackgroundAttributes background) {
        return switch (background) {
            case BackgroundAttributes.Image image -> image.key();
            case BackgroundAttributes.Color color -> null;
        };
    }

    // ── 생성 조립 — "DB에 URL을 저장하지 않는다"는 정규화 규칙을 서비스가 아니라 여기서 강제한다 ──

    public Frame assembleOwned(User user, FrameCreateRequest request) {
        Frame frame = Frame.owned(user, request.title(), request.descriptionOrEmpty(),
                frameAssetManager.normalizeImageKey(request.previewKey()),
                request.frameType(), normalizeBackground(request.background()));
        createComponents(request.components()).forEach(frame::addComponent);
        return frame;
    }

    public Frame assembleSystem(FrameCreateRequest request) {
        Frame frame = Frame.system(request.title(), request.descriptionOrEmpty(),
                frameAssetManager.normalizeImageKey(request.previewKey()),
                request.frameType(), normalizeBackground(request.background()));
        createComponents(request.components()).forEach(frame::addComponent);
        return frame;
    }

    // 전체 교체 수정의 공통 절차: 메타데이터 갱신 + 컴포넌트 재조립 + 참조 잃은 key 삭제 예약.
    // 사용자/관리자 서비스가 각자의 관문 뒤에서 부른다 — 수집 규칙이 두 벌로 갈라지면 안 되는 코드다
    public void replaceContent(Frame frame, FrameCreateRequest request) {
        String oldBackgroundKey = extractBackgroundKey(frame.getBackground());
        String oldPreviewKey = frame.getPreviewKey();
        List<String> oldAssetKeys = extractAssetKeys(frame.getComponents());

        BackgroundAttributes newBackground = normalizeBackground(request.background());
        String newPreviewKey = frameAssetManager.normalizeImageKey(request.previewKey());
        frame.updateMetadata(request.title(), request.descriptionOrEmpty(), newBackground, newPreviewKey);

        frame.clearComponents();
        List<FrameComponent> newComponents = createComponents(request.components());
        newComponents.forEach(frame::addComponent);

        // 교체로 참조를 잃은 key만 수집 — 계속 쓰이는 key를 지우면 멀쩡한 프레임이 깨진다.
        // 실제 삭제는 커밋 후(AFTER_COMMIT)라 이 아래에서 롤백돼도 파일은 무사하다
        List<String> garbage = new ArrayList<>();
        String newBackgroundKey = extractBackgroundKey(newBackground);
        if (oldBackgroundKey != null && !oldBackgroundKey.equals(newBackgroundKey)) {
            garbage.add(oldBackgroundKey);
        }
        if (!oldPreviewKey.equals(newPreviewKey)) {
            garbage.add(oldPreviewKey);
        }
        Set<String> keptAssetKeys = Set.copyOf(extractAssetKeys(newComponents));
        oldAssetKeys.stream().filter(key -> !keptAssetKeys.contains(key)).forEach(garbage::add);
        frameAssetManager.deleteAfterCommit(garbage);
    }

    // 삭제 시 예약할 key 전부: 사진·구운 텍스트 + 배경 + 프리뷰.
    // COLOR 배경의 null은 deleteAfterCommit 필터가 거른다
    public List<String> collectAllKeys(Frame frame) {
        List<String> keys = new ArrayList<>(extractAssetKeys(frame.getComponents()));
        keys.add(extractBackgroundKey(frame.getBackground()));
        keys.add(frame.getPreviewKey());
        return keys;
    }

    public FrameResponse toFrameResponse(Frame frame) {
        List<FrameResponse.ComponentResponse> components = frame.getComponents().stream()
                .sorted(Comparator.comparingInt(FrameComponent::getZIndex))
                .map(c -> FrameResponse.ComponentResponse.of(
                        c, frameAssetManager.resolveSource(c.getType(), c.getSource())))
                .toList();

        return FrameResponse.of(
                frame,
                frameAssetManager.resolveImageSource(frame.getPreviewKey()),
                resolveBackgroundUrl(frame.getBackground()),
                components);
    }

    // IMAGE 배경에만 presigned url 주입 — url은 응답에서만 존재하는 필드
    private BackgroundAttributes resolveBackgroundUrl(BackgroundAttributes background) {
        return switch (background) {
            case BackgroundAttributes.Image image ->
                    image.withUrl(frameAssetManager.resolveImageSource(image.key()));
            case BackgroundAttributes.Color color -> color;
        };
    }
}
