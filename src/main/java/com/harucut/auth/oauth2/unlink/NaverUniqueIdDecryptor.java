package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 네이버 연동 해제 웹훅 페이로드 검증·복호화.
 *
 * <p>서명(HMAC-SHA256)과 암호화(AES/CBC, MD5로 늘인 키, 앞 16바이트가 IV)는
 * 네이버가 정한 스펙이라 여기 상수들은 바꿀 수 없다.
 */
@Component
public class NaverUniqueIdDecryptor {

    private static final String ALGORITHM_AES_CBC_PKCS5 = "AES/CBC/PKCS5Padding";
    private static final String ALGORITHM_AES = "AES";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int BLOCK_SIZE = 16;

    private final String clientSecret;

    public NaverUniqueIdDecryptor(
            @Value("${spring.security.oauth2.client.registration.naver.client-secret}") String clientSecret) {
        this.clientSecret = clientSecret;
    }

    public String verifyAndDecrypt(NaverUnlinkRequest request) {
        if (request.clientId() == null || request.encryptUniqueId() == null
                || request.timestamp() == null || request.signature() == null) {
            throw new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED);
        }
        if (!verifySignature(request)) {
            throw new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED);
        }
        try {
            return decryptUniqueId(request.encryptUniqueId());
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 서명이 맞는데 복호화가 깨지면 페이로드가 네이버 스펙과 어긋난 것이다
            throw new BusinessException(AuthErrorCode.OAUTH2_UNLINK_FAILED);
        }
    }

    private boolean verifySignature(NaverUnlinkRequest request) {
        String data = request.clientId() + request.encryptUniqueId() + request.timestamp();
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String calculated = Base64.getUrlEncoder().withoutPadding().encodeToString(rawHmac);
            // equals는 다른 첫 글자에서 비교를 멈춘다 — 응답 시간으로 서명이 새지 않게 상수 시간 비교
            return MessageDigest.isEqual(
                    calculated.getBytes(StandardCharsets.UTF_8),
                    request.signature().getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private String decryptUniqueId(String encrypted) throws GeneralSecurityException {
        byte[] aesKey = MessageDigest.getInstance("MD5").digest(clientSecret.getBytes(StandardCharsets.UTF_8));
        byte[] encryptedWithIv = Base64.getUrlDecoder().decode(encrypted);
        byte[] iv = Arrays.copyOfRange(encryptedWithIv, 0, BLOCK_SIZE);
        byte[] cipherText = Arrays.copyOfRange(encryptedWithIv, BLOCK_SIZE, encryptedWithIv.length);

        Cipher cipher = Cipher.getInstance(ALGORITHM_AES_CBC_PKCS5);
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, ALGORITHM_AES), new IvParameterSpec(iv));
        return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    }
}
