package com.harucut.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ProviderTest {

    @Test
    @DisplayName("소문자 registrationId를 enum으로 바꾼다")
    void fromRegistrationId() {
        assertThat(Provider.from("kakao")).isEqualTo(Provider.KAKAO);
    }

    @Test
    @DisplayName("등록되지 않은 registrationId면 예외를 던진다")
    void unknownRegistrationId() {
        assertThatThrownBy(() -> Provider.from("line"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}