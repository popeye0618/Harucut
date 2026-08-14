package com.harucut.auth.oauth2;

import com.harucut.auth.cookie.CookieManager;
import com.harucut.auth.jwt.IssuedToken;
import com.harucut.auth.security.AuthenticatedUser;
import com.harucut.auth.service.JwtTokenService;
import com.harucut.auth.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private static final String CALLBACK_PATH = "/oauth2/callback";

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final CookieManager cookieManager;
    private final String callbackUrl;

    public CustomOAuth2SuccessHandler(JwtTokenService jwtTokenService,
                                      RefreshTokenService refreshTokenService,
                                      CookieManager cookieManager,
                                      @Value("${frontend.url}") String frontendUrl) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.cookieManager = cookieManager;
        this.callbackUrl = frontendUrl + CALLBACK_PATH;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        AuthenticatedUser user = ((CustomOAuth2User) authentication.getPrincipal()).authenticatedUser();

        IssuedToken accessToken = jwtTokenService.createAccessToken(user.publicId(), user.role(), user.status());
        IssuedToken refreshToken = jwtTokenService.createRefreshToken(user.publicId());
        refreshTokenService.save(user.publicId(), refreshToken);

        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieManager.createTokenCookie(CookieManager.ACCESS_TOKEN, accessToken).toString());
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieManager.createTokenCookie(CookieManager.REFRESH_TOKEN, refreshToken).toString());

        response.sendRedirect(callbackUrl);
    }
}
