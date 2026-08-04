package com.harucut.common.utils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PublicIdsTest {

    private static final String ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    @Test
    @DisplayName("길이가 12이고 허용된 문자만 사용한다")
    void format() {
        String id = PublicIds.generate();
        assertThat(id)
                .hasSize(12)
                .matches("^[" + ALPHABET + "]+");
    }

    @Test
    @DisplayName("1만 번 생성해도 중복이 없다")
    void noDuplicate() {
        Set<String> ids = Stream.generate(PublicIds::generate)
                .limit(10000)
                .collect(Collectors.toSet());

        assertThat(ids).hasSize(10000);
    }

    @Test
    @DisplayName("문자 분포가 균등하다 (modulo bias 검출)")
    void uniformDistribution() {
        int samples = 100000;
        Map<Character, Long> counts = Stream.generate(PublicIds::generate)
                .limit(samples)
                .flatMap(id -> id.chars().mapToObj(c -> (char) c))
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));

        long expected = (long) samples * 12 / ALPHABET.length();

        assertThat(counts)
                .hasSize(ALPHABET.length())
                .allSatisfy((ch, count) ->
                        assertThat(count).isBetween((long) (expected * 0.9), (long) (expected * 1.1)));
    }
}