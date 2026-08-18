package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class KakaoOAuth2UnlinkService implements OAuth2UnlinkService {

    // 카카오 에러 코드 -101: 이 앱에 연결되지 않은 사용자 — 이미 카카오 쪽에서 끊었다는 뜻
    private static final int NOT_REGISTERED_USER = -101;

    private final RestClient restClient;
    private final KakaoAuthProperties properties;
    private final ObjectMapper objectMapper;

    public KakaoOAuth2UnlinkService(RestClient.Builder restClientBuilder,
                                    KakaoAuthProperties properties,
                                    ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(Provider provider) {
        return provider == Provider.KAKAO;
    }

    @Override
    public void unlink(User user) {
        if (user.getProvider() != Provider.KAKAO) {
            return;
        }
        String targetId = user.getProviderId();
        if (targetId == null) {
            // 여기서 던지면 이 사용자는 매일 밤 실패만 반복한다 — 연결 해제 없이 탈퇴를 진행시킨다
            log.error("[Kakao unlink] providerId 없는 카카오 사용자 — 연결 해제 없이 탈퇴를 계속한다. userId={}",
                    user.getId());
            return;
        }

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("target_id_type", properties.unlinkTargetIdType());
        body.add("target_id", targetId);

        try {
            restClient.post()
                    .uri(properties.unlinkUrl())
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + properties.adminKey())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            if (isAlreadyUnlinked(e)) {
                log.info("[Kakao unlink] 이미 연결이 끊긴 사용자 — 성공으로 취급한다. targetId={}", targetId);
                return;
            }
            log.warn("[Kakao unlink] 실패 targetId={} status={} body={}",
                    targetId, e.getStatusCode(), e.getResponseBodyAsString(), e);
            throw new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED);
        } catch (RestClientException e) {
            log.warn("[Kakao unlink] 호출 실패 targetId={}", targetId, e);
            throw new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED);
        }
    }

    private boolean isAlreadyUnlinked(RestClientResponseException e) {
        try {
            return objectMapper.readTree(e.getResponseBodyAsString()).path("code").asInt() == NOT_REGISTERED_USER;
        } catch (JacksonException parseFailure) {
            return false;
        }
    }
}
