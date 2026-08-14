package com.harucut.auth.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class CustomOAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String CALLBACK_PATH = "/oauth2/callback?error=oauth2";

    private final String callbackUrl;

    public CustomOAuth2FailureHandler(@Value("${frontend.url}") String frontendUrl) {
        this.callbackUrl = frontendUrl + CALLBACK_PATH;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.warn("[oauth2] 인증 실패", exception);
        response.sendRedirect(callbackUrl);
    }
}
