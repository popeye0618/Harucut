package com.harucut.frame.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.frame.entity.Frame;
import com.harucut.frame.repository.FrameRepository;
import com.harucut.frame.service.FrameAssetManager;
import com.harucut.frame.service.FrameComponentAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class FrameUserDeletionHandler implements UserDeletionHandler {

    private final FrameRepository frameRepository;
    private final FrameComponentAssembler frameComponentAssembler;
    private final FrameAssetManager frameAssetManager;

    // JPQL 벌크 DELETE는 cascade/orphanRemoval을 우회하므로 컴포넌트를 직접, 먼저 지운다 (FK)
    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        List<Frame> frames = frameRepository.findAllWithComponentsByUserId(userId);
        List<String> keys = frames.stream()
                .flatMap(frame -> frameComponentAssembler.collectAllKeys(frame).stream())
                .toList();

        frameRepository.deleteComponentsByUserId(userId);
        frameRepository.deleteByUserId(userId);

        frameAssetManager.deleteAfterCommit(keys);
    }
}
