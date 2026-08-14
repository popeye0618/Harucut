package com.harucut.support;

import com.harucut.auth.oauth2.CustomOAuth2FailureHandler;
import com.harucut.auth.oauth2.CustomOAuth2SuccessHandler;
import com.harucut.auth.oauth2.CustomOAuth2UserService;
import com.harucut.auth.oauth2.CustomOidcUserService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code @WebMvcTest} + {@code @Import(SecurityConfig.class)} 테스트의 공통 베이스.
 *
 * <p>SecurityConfig 가 생성자로 요구하지만 개별 테스트의 관심사가 아닌 빈을 여기서 mock 으로 채운다.
 * JwtTokenService 처럼 테스트마다 진짜 빈을 쓰는지 mock 을 쓰는지 갈리는 것은 여기 두지 않는다.
 */
public abstract class SecurityBeansMockSupport {

    @MockitoBean
    protected CustomOAuth2UserService oAuth2UserService;

    @MockitoBean
    protected CustomOidcUserService oidcUserService;

    @MockitoBean
    protected CustomOAuth2SuccessHandler oAuth2SuccessHandler;

    @MockitoBean
    protected CustomOAuth2FailureHandler oAuth2FailureHandler;
}
