package com.harucut.media.handler;

import com.harucut.auth.service.UserDeletionHandler;
import com.harucut.media.repository.ComposeJobRepository;
import com.harucut.media.repository.UserMediaRepository;
import com.harucut.storage.event.S3DeleteEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserMediaDeletionHandler implements UserDeletionHandler {

    private final UserMediaRepository userMediaRepository;
    private final ComposeJobRepository composeJobRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 키를 먼저 모은다(행이 지워지면 못 읽는다) → 행 삭제 → S3는 커밋 후 삭제.
    // 롤백되면 이벤트도 안 나가므로 파일이 억울하게 사라질 일이 없다.
    @Transactional
    @Override
    public void handleUserDeletion(Long userId) {
        List<String> keys = new ArrayList<>();
        keys.addAll(userMediaRepository.findS3KeysByUserId(userId));
        keys.addAll(userMediaRepository.findThumbnailKeysByUserId(userId));
        keys.addAll(composeJobRepository.findResultKeysByUserId(userId));

        composeJobRepository.deleteByUserId(userId);
        userMediaRepository.deleteByUserId(userId);

        if (!keys.isEmpty()) {
            eventPublisher.publishEvent(new S3DeleteEvent(keys));
        }
    }
}
