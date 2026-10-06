package com.example.resay.global.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AudioUrlSignerTest {

    private static final Instant NOW = Instant.parse("2026-10-07T03:00:00Z");
    private static final String SECRET = "test-only-audio-url-secret-0123456789";

    private final AudioUrlSigner signer = new AudioUrlSigner(SECRET, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 서명한_주소는_10분_동안_유효하다() {
        AudioUrlSigner.SignedAudioUrl signed = signer.sign(1L);

        assertThat(signed.expires()).isEqualTo(NOW.plusSeconds(600).getEpochSecond());
        assertThat(signer.verify(1L, signed.expires(), signed.signature())).isTrue();
    }

    @Test
    void 만료되면_거부한다() {
        AudioUrlSigner.SignedAudioUrl signed = signer.sign(1L);
        AudioUrlSigner later = new AudioUrlSigner(SECRET, Clock.fixed(NOW.plusSeconds(601), ZoneOffset.UTC));

        assertThat(later.verify(1L, signed.expires(), signed.signature())).isFalse();
    }

    @Test
    void 다른_녹음이나_바꾼_만료시각이나_틀린_서명은_거부한다() {
        AudioUrlSigner.SignedAudioUrl signed = signer.sign(1L);

        assertThat(signer.verify(2L, signed.expires(), signed.signature())).isFalse();
        assertThat(signer.verify(1L, signed.expires() + 3600, signed.signature())).isFalse();
        assertThat(signer.verify(1L, signed.expires(), "wrong")).isFalse();
        assertThat(signer.verify(1L, signed.expires(), null)).isFalse();
    }

    @Test
    void 키가_다르면_거부한다() {
        AudioUrlSigner.SignedAudioUrl signed = signer.sign(1L);
        AudioUrlSigner otherKey = new AudioUrlSigner("another-secret-0123456789-abcdefgh", Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(otherKey.verify(1L, signed.expires(), signed.signature())).isFalse();
    }
}
