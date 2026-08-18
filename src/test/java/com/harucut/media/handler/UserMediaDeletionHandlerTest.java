package com.harucut.media.handler;

import com.harucut.media.repository.ComposeJobRepository;
import com.harucut.media.repository.UserMediaRepository;
import com.harucut.storage.event.S3DeleteEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserMediaDeletionHandler")
class UserMediaDeletionHandlerTest {

    @Mock
    private UserMediaRepository userMediaRepository;

    @Mock
    private ComposeJobRepository composeJobRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserMediaDeletionHandler handler;

    @Test
    @DisplayName("원본·썸네일·합성 결과 키가 하나의 S3 삭제 이벤트로 발행된다")
    void publishesAllKeys() {
        given(userMediaRepository.findS3KeysByUserId(1L)).willReturn(List.of("m1.png"));
        given(userMediaRepository.findThumbnailKeysByUserId(1L)).willReturn(List.of("m1-thumb.jpg"));
        given(composeJobRepository.findResultKeysByUserId(1L)).willReturn(List.of("result.png"));

        handler.handleUserDeletion(1L);

        ArgumentCaptor<S3DeleteEvent> captor = ArgumentCaptor.forClass(S3DeleteEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().keys())
                .containsExactly("m1.png", "m1-thumb.jpg", "result.png");
        then(composeJobRepository).should().deleteByUserId(1L);
        then(userMediaRepository).should().deleteByUserId(1L);
    }

    @Test
    @DisplayName("지울 키가 없으면 이벤트를 발행하지 않는다")
    void skipsEventWhenNoKeys() {
        given(userMediaRepository.findS3KeysByUserId(1L)).willReturn(List.of());
        given(userMediaRepository.findThumbnailKeysByUserId(1L)).willReturn(List.of());
        given(composeJobRepository.findResultKeysByUserId(1L)).willReturn(List.of());

        handler.handleUserDeletion(1L);

        then(eventPublisher).shouldHaveNoInteractions();
    }
}
