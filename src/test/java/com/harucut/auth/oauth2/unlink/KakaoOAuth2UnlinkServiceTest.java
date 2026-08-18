package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import com.harucut.support.UserFixtures;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("KakaoOAuth2UnlinkService")
class KakaoOAuth2UnlinkServiceTest {

    private static final String UNLINK_URL = "https://kapi.kakao.test/v1/user/unlink";
    private static final KakaoAuthProperties PROPERTIES =
            new KakaoAuthProperties("test-admin-key", UNLINK_URL, "user_id");

    private MockRestServiceServer server;
    private KakaoOAuth2UnlinkService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new KakaoOAuth2UnlinkService(builder, PROPERTIES, JsonMapper.builder().build());
    }

    @Test
    @DisplayName("카카오 provider만 지원한다")
    void supportsOnlyKakao() {
        assertThat(service.supports(Provider.KAKAO)).isTrue();
        assertThat(service.supports(Provider.NAVER)).isFalse();
        assertThat(service.supports(Provider.HARUCUT)).isFalse();
    }

    @Test
    @DisplayName("admin key와 providerId로 연결 끊기를 호출한다")
    void unlinksWithAdminKeyAndProviderId() {
        MultiValueMap<String, String> expectedBody = new LinkedMultiValueMap<>();
        expectedBody.add("target_id_type", "user_id");
        expectedBody.add("target_id", "kakao-12345");

        server.expect(requestTo(UNLINK_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "KakaoAK test-admin-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedBody))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        service.unlink(kakaoUser("kakao-12345"));

        server.verify();
    }

    // 카카오 쪽에서 이미 끊은 사용자를 실패로 취급하면 매일 밤 실패만 반복하는 좀비가 된다
    @Test
    @DisplayName("이미 연결이 끊긴 사용자(-101)는 성공으로 취급한다")
    void treatsAlreadyUnlinkedAsSuccess() {
        server.expect(requestTo(UNLINK_URL))
                .andRespond(withBadRequest()
                        .body("{\"msg\":\"[NotRegisteredUserException] user not found\",\"code\":-101}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatCode(() -> service.unlink(kakaoUser("kakao-12345")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("-101이 아닌 에러 응답은 AUTH-091로 터진다")
    void otherErrorResponseFails() {
        server.expect(requestTo(UNLINK_URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"msg\":\"invalid app key\",\"code\":-401}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.unlink(kakaoUser("kakao-12345")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    @Test
    @DisplayName("네트워크 장애도 AUTH-091로 터진다 — 배치가 내일 재시도한다")
    void networkFailureFails() {
        server.expect(requestTo(UNLINK_URL))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> service.unlink(kakaoUser("kakao-12345")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    @Test
    @DisplayName("카카오 사용자가 아니면 호출하지 않는다")
    void ignoresNonKakaoUser() {
        service.unlink(UserFixtures.socialUser("naver@harucut.com", Provider.NAVER, "naver-1"));

        server.verify();
    }

    // 소셜 사용자의 providerId가 없는 건 데이터 이상이지만, 던지면 영원히 탈퇴하지 못한다
    @Test
    @DisplayName("providerId가 없으면 호출 없이 넘어간다")
    void skipsWhenProviderIdMissing() {
        assertThatCode(() -> service.unlink(UserFixtures.socialUser("kakao@harucut.com", Provider.KAKAO)))
                .doesNotThrowAnyException();

        server.verify();
    }

    private User kakaoUser(String providerId) {
        return UserFixtures.socialUser("kakao@harucut.com", Provider.KAKAO, providerId);
    }
}
