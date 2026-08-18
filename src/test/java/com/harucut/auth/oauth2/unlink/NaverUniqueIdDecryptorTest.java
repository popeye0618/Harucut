package com.harucut.auth.oauth2.unlink;

import com.harucut.auth.dto.NaverUnlinkRequest;
import com.harucut.auth.exception.AuthErrorCode;
import com.harucut.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NaverUniqueIdDecryptor")
class NaverUniqueIdDecryptorTest {

    private static final String SECRET = "test-naver-client-secret";
    private static final String CLIENT_ID = "test-naver-client-id";
    private static final String TIMESTAMP = "1755450000";

    private final NaverUniqueIdDecryptor decryptor = new NaverUniqueIdDecryptor(SECRET);

    @Test
    @DisplayName("네이버 스펙대로 암호화된 uniqueId를 복호화한다")
    void decryptsValidPayload() throws Exception {
        String encrypted = encrypt("naver-user-123");

        String providerId = decryptor.verifyAndDecrypt(
                new NaverUnlinkRequest(CLIENT_ID, encrypted, TIMESTAMP, sign(encrypted)));

        assertThat(providerId).isEqualTo("naver-user-123");
    }

    @Test
    @DisplayName("서명이 일치하지 않으면 AUTH-091로 터지고 복호화하지 않는다")
    void rejectsBadSignature() throws Exception {
        String encrypted = encrypt("naver-user-123");

        assertThatThrownBy(() -> decryptor.verifyAndDecrypt(
                new NaverUnlinkRequest(CLIENT_ID, encrypted, TIMESTAMP, "forged-signature")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    // clientId·timestamp도 서명 대상이다 — 하나만 바뀌어도 같은 암호문이 거부되어야 한다
    @Test
    @DisplayName("timestamp를 바꾼 재사용 페이로드는 거부된다")
    void rejectsTamperedTimestamp() throws Exception {
        String encrypted = encrypt("naver-user-123");
        String signature = sign(encrypted);

        assertThatThrownBy(() -> decryptor.verifyAndDecrypt(
                new NaverUnlinkRequest(CLIENT_ID, encrypted, "9999999999", signature)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    @Test
    @DisplayName("서명은 맞지만 암호문이 깨져 있으면 AUTH-091로 터진다")
    void rejectsBrokenCiphertext() throws Exception {
        // 서명은 base64 해석 전의 문자열 위에서 계산되므로, base64가 아니어도 서명 검증은 통과한다
        String broken = "not-base64!!!";

        assertThatThrownBy(() -> decryptor.verifyAndDecrypt(
                new NaverUnlinkRequest(CLIENT_ID, broken, TIMESTAMP, signRaw(CLIENT_ID + broken + TIMESTAMP))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    @Test
    @DisplayName("필드가 하나라도 없으면 AUTH-091로 터진다")
    void rejectsMissingField() throws Exception {
        String encrypted = encrypt("naver-user-123");

        assertThatThrownBy(() -> decryptor.verifyAndDecrypt(
                new NaverUnlinkRequest(CLIENT_ID, encrypted, TIMESTAMP, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(AuthErrorCode.OAUTH2_UNLINK_FAILED);
    }

    // ── 네이버 송신측 재현 ──────────────────────────
    // 프로덕션 코드의 역연산이 아니라 네이버 문서의 송신 스펙을 따로 구현한 것이다.
    // 둘이 같은 실수를 공유하면 이 테스트는 그걸 잡지 못한다 — 스펙 변경 시 양쪽을 다 봐야 한다.

    private String encrypt(String plain) throws Exception {
        byte[] aesKey = MessageDigest.getInstance("MD5").digest(SECRET.getBytes(StandardCharsets.UTF_8));
        byte[] iv = "0123456789abcdef".getBytes(StandardCharsets.UTF_8);

        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"), new IvParameterSpec(iv));
        byte[] cipherText = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

        byte[] withIv = ByteBuffer.allocate(iv.length + cipherText.length).put(iv).put(cipherText).array();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(withIv);
    }

    private String sign(String encrypted) throws Exception {
        return signRaw(CLIENT_ID + encrypted + TIMESTAMP);
    }

    private String signRaw(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
