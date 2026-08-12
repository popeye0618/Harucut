package com.harucut.auth.security;

import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.auth.exception.CustomAuthenticationException;
import com.harucut.common.exception.ErrorCode;
import com.harucut.common.response.Response;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ErrorCode errorCode = (authException instanceof CustomAuthenticationException e)
                ? e.getErrorCode()
                : AuthErrorCode.AUTHENTICATION_FAILED;

        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Response<Void> body = Response.error(errorCode);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
