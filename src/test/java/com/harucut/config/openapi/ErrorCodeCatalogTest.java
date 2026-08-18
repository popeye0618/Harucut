package com.harucut.config.openapi;

import com.harucut.subscription.exception.SubscriptionErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ErrorCodeCatalog")
class ErrorCodeCatalogTest {

    @Nested
    @DisplayName("require")
    class Require {

        @Test
        @DisplayName("도메인 enum 에 선언된 코드를 그 enum 상수 그대로 돌려준다")
        void resolvesToDeclaringEnumConstant() {
            assertThat(ErrorCodeCatalog.require("SUBS-002"))
                    .isSameAs(SubscriptionErrorCode.PLAN_HISTORY_RETENTION_EXCEEDED);
        }

        @Test
        @DisplayName("없는 코드는 예외로 막는다 — 문서의 오타가 기동 시점에 드러난다")
        void unknownCodeFails() {
            assertThatThrownBy(() -> ErrorCodeCatalog.require("SUBS-999"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SUBS-999");
        }
    }
}
