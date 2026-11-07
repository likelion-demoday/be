package com.example.resay.domain.notification.service;

import com.example.resay.global.security.JwtProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

// 광고 메일의 수신 거부 링크는 로그인 없이 눌러야 하므로, 사용자별로 서명한 값으로 본인 링크인지 확인한다.
// 예전 메일의 링크로도 거부할 수 있어야 해서 만료 시각은 두지 않는다.
// 로그인 토큰과 같은 키를 쓰되 용도 문자열을 함께 서명해 다른 용도로 재사용되지 않게 한다
@Component
public class UnsubscribeTokenSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final String PURPOSE = "marketing-unsubscribe";

    private final byte[] key;

    @Autowired
    public UnsubscribeTokenSigner(JwtProperties jwtProperties) {
        this(jwtProperties.secret());
    }

    public UnsubscribeTokenSigner(String secret) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(Long userId) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal((PURPOSE + ":" + userId).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("수신 거부 링크 서명에 실패했습니다.", exception);
        }
    }

    public boolean verify(Long userId, String token) {
        if (userId == null || token == null) {
            return false;
        }
        // 비교 시간으로 값을 추측할 수 없도록 고정 시간 비교를 쓴다
        return MessageDigest.isEqual(
                sign(userId).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }
}
