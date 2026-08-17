package com.harucut.storage.event;

import com.harucut.storage.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3DeleteListener")
class S3DeleteListenerTest {

    @Mock
    private FileStorageService fileStorageService;

    private S3DeleteListener listener;

    @BeforeEach
    void setUp() {
        listener = new S3DeleteListener(fileStorageService);
    }

    @Test
    @DisplayName("이벤트의 모든 key를 삭제한다")
    void deletesAllKeys() {
        listener.on(new S3DeleteEvent(List.of("uploads/a.png", "uploads/b.png")));

        then(fileStorageService).should().delete("uploads/a.png");
        then(fileStorageService).should().delete("uploads/b.png");
    }

    @Test
    @DisplayName("하나가 실패해도 나머지는 지우고 예외를 전파하지 않는다 — 커밋 후 예외는 '저장됐는데 500'이 된다")
    void bestEffortOnFailure() {
        willThrow(new RuntimeException("S3 장애")).given(fileStorageService).delete("uploads/a.png");

        assertThatCode(() -> listener.on(new S3DeleteEvent(List.of("uploads/a.png", "uploads/b.png"))))
                .doesNotThrowAnyException();

        then(fileStorageService).should().delete("uploads/b.png");
    }
}
