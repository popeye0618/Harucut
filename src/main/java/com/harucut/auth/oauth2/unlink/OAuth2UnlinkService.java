package com.harucut.auth.oauth2.unlink;

import com.harucut.user.entity.User;
import com.harucut.user.enums.Provider;

/**
 * 탈퇴(exit) 시점에 우리가 제공자를 호출해 연결을 끊는 아웃바운드 unlink.
 *
 * <p>네이버는 이 인터페이스를 구현하지 않는다 — 토큰을 저장하지 않아 아웃바운드가 불가능하고,
 * 대신 네이버가 우리 웹훅을 호출하는 인바운드 경로({@link NaverOAuth2UnlinkService})만 있다.
 */
public interface OAuth2UnlinkService {

    boolean supports(Provider provider);

    void unlink(User user);
}
