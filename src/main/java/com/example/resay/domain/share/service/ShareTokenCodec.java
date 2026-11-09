package com.example.resay.domain.share.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class ShareTokenCodec {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_-]{43}");

    private final SecureRandom secureRandom;

    public ShareTokenCodec() {
        this(new SecureRandom());
    }

    ShareTokenCodec(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String extractAndHash(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Share ")) {
            return null;
        }
        String token = authorizationHeader.substring("Share ".length());
        if (!TOKEN_PATTERN.matcher(token).matches()) {
            return null;
        }
        return hash(token);
    }

    public String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
