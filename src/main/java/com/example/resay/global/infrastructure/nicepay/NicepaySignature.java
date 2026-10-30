package com.example.resay.global.infrastructure.nicepay;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 나이스페이가 보낸 값이 중간에 바뀌지 않았는지 확인하는 서명 검증.
 * 서명은 시크릿 키를 아는 쪽만 만들 수 있다: hex(sha256(값들을 이어 붙인 문자열 + 시크릿 키))
 */
@Component
@RequiredArgsConstructor
public class NicepaySignature {

    private final NicepayProperties properties;

    /** 결제창 인증 결과의 서명: authToken + clientId + amount + 시크릿 키 */
    public boolean matchesAuthResult(String authToken, String clientId, int amount, String signature) {
        if (authToken == null || clientId == null) {
            return false;
        }
        return matches(authToken + clientId + amount, signature);
    }

    /** 승인 · 조회 · 취소 응답의 서명: tid + amount + ediDate(응답에 온 문자열 그대로) + 시크릿 키 */
    public NicepayTransaction.Signature checkTransaction(String tid, int amount, String ediDate, String signature) {
        if (isBlank(tid) || isBlank(ediDate) || isBlank(signature)) {
            return NicepayTransaction.Signature.MISSING;
        }
        return matches(tid + amount + ediDate, signature)
                ? NicepayTransaction.Signature.VALID
                : NicepayTransaction.Signature.INVALID;
    }

    /**
     * 인증 토큰을 저장 · 비교할 때 쓰는 값 (SHA-256). 토큰 자체는 저장하지 않는다.
     * 같은 인증 결과가 다른 주문에 다시 쓰였는지 알아보는 데 쓴다.
     */
    public String fingerprint(String authToken) {
        return sha256Hex(authToken == null ? "" : authToken);
    }

    private boolean matches(String data, String signature) {
        if (signature == null || signature.isBlank() || !properties.isConfigured()) {
            return false;
        }
        byte[] expected = sha256Hex(data + properties.secretKey()).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = signature.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        // 앞에서부터 비교하다 다른 글자에서 멈추면 걸린 시간으로 맞는 글자 수를 추측할 수 있어 끝까지 비교한다
        return MessageDigest.isEqual(expected, actual);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
