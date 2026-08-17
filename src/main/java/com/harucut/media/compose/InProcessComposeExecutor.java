package com.harucut.media.compose;

import com.harucut.frame.attributes.BackgroundAttributes;
import com.harucut.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 서버 프로세스 안에서 그리는 실행기 — 로컬 개발·테스트의 영구 경로.
// 운영은 6단계의 Lambda 구현이 대신하지만, 둘 다 같은 FourcutRenderer를 쓰므로 픽셀이 같다
@Component
@RequiredArgsConstructor
public class InProcessComposeExecutor implements ComposeExecutor {

    private final FileStorageService fileStorageService;
    private final FourcutRenderer fourcutRenderer;

    @Override
    public void execute(ComposeSpec spec, List<String> sourceKeys, String resultKey) {
        List<byte[]> sources = sourceKeys.stream()
                .map(fileStorageService::downloadBytes)
                .toList();

        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String key : assetKeysOf(spec)) {
            assets.computeIfAbsent(key, fileStorageService::downloadBytes);
        }

        fileStorageService.uploadBytes(resultKey,
                fourcutRenderer.render(spec, sources, assets), "image/png");
    }

    // 스펙이 참조하는 자산 전부: IMAGE 배경 + 레이어. computeIfAbsent가 중복 다운로드를 거른다
    private List<String> assetKeysOf(ComposeSpec spec) {
        List<String> keys = new ArrayList<>();
        if (spec.background() instanceof BackgroundAttributes.Image image) {
            keys.add(image.key());
        }
        spec.layers().forEach(layer -> keys.add(layer.source()));
        return keys;
    }
}
