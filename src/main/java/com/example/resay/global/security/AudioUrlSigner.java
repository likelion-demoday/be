package com.example.resay.global.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

// 브라우저 <audio> 태그는 인증 헤더를 붙일 수 없어, 녹음별로 서명한 임시 재생 주소로 접근을 허용한다
// 로그인 토큰과 같은 키를 쓰되 용도 문자열을 함께 서명해 다른 용도로 재사용되지 않게 한다
@Component
public class AudioUrlSigner {

    static final Duration VALIDITY = Duration.ofMinutes(10);
    private static final String ALGORITHM = "HmacSHA256";
    private static final String PURPOSE = "recording-audio";

    private final byte[] key;
    private final Clock clock;

    @Autowired
    public AudioUrlSigner(JwtProperties jwtProperties) {
        this(jwtProperties.secret(), Clock.systemUTC());
    }

    AudioUrlSigner(String secret, Clock clock) {
        this.key = secret.getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public SignedAudioUrl sign(Long recordingId) {
        long expires = clock.instant().plus(VALIDITY).getEpochSecond();
        return new SignedAudioUrl(expires, signature(recordingId, expires));
    }

    public boolean verify(Long recordingId, long expires, String signature) {
        if (signature == null || clock.instant().getEpochSecond() > expires) {
            return false;
        }
        // 서명 비교 시간으로 값을 추측할 수 없도록 고정 시간 비교를 쓴다
        return MessageDigest.isEqual(
                signature(recordingId, expires).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String signature(Long recordingId, long expires) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            byte[] digest = mac.doFinal((PURPOSE + ":" + recordingId + ":" + expires).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("재생 주소 서명에 실패했습니다.", e);
        }
    }

    public record SignedAudioUrl(long expires, String signature) {
    }
}
