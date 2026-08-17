package com.harucut.media.compose;

import com.harucut.storage.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// 서버 프로세스 안에서 그리는 실행기 — 로컬 개발·테스트의 영구 경로.
// 운영은 compose.executor=lambda로 LambdaComposeExecutor가 대신하지만,
// 둘 다 같은 FourcutRenderer(compose-core)를 쓰므로 픽셀이 같다
@Component
@ConditionalOnProperty(name = "compose.executor", havingValue = "in-process", matchIfMissing = true)
@RequiredArgsConstructor
public class InProcessComposeExecutor implements ComposeExecutor {

    private final FileStorageService fileStorageService;
    private final FourcutRenderer fourcutRenderer;

    @Override
    public void execute(ComposeSpec spec, List<String> sourceKeys, String resultKey) {
        List<byte[]> sources = sourceKeys.stream()
                .map(fileStorageService::downloadBytes)
                .toList();

        // 자산 목록은 스펙이 소유한다(referencedAssetKeys) — Lambda 핸들러와 같은 계산
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String key : spec.referencedAssetKeys()) {
            assets.put(key, fileStorageService.downloadBytes(key));
        }

        fileStorageService.uploadBytes(resultKey,
                fourcutRenderer.render(spec, sources, assets), "image/png");
    }
}
