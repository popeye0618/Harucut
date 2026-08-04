package com.harucut.common.response;

import com.harucut.common.exception.GlobalErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

import static org.assertj.core.api.Assertions.assertThat;

@JsonTest
class ResponseTest {

    @Autowired
    private JacksonTester<Response<?>> json;

    @Test
    @DisplayName("데이터 없는 성공 응답에는 message와 data 키가 없다")
    void okWithoutData() throws Exception {
        assertThat(json.write(Response.ok()))
                .extractingJsonPathStringValue("$.code").isEqualTo("GEN-000");

        assertThat(json.write(Response.ok()))
                .doesNotHaveJsonPath("$.message")
                .doesNotHaveJsonPath("$.data");
    }

    @Test
    @DisplayName("에러 응답에는 code, status, message가 모두 있고 data는 없다")
    void error() throws Exception {
        assertThat(json.write(Response.error(GlobalErrorCode.NOT_FOUND)))
                .extractingJsonPathStringValue("$.code").isEqualTo("GEN-031");

        assertThat(json.write(Response.error(GlobalErrorCode.NOT_FOUND)))
                .extractingJsonPathNumberValue("$.status").isEqualTo(404);

        assertThat(json.write(Response.error(GlobalErrorCode.NOT_FOUND)))
                .doesNotHaveJsonPath("$.data");
    }
}