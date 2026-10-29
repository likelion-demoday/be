package com.example.resay.domain.auth.service;

import com.example.resay.domain.auth.service.SocialSignupTokenStore.PendingSocialSignup;
import com.example.resay.domain.user.entity.Provider;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SocialSignupTokenStoreTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-28T00:00:00Z"));
    private final SocialSignupTokenStore store = new SocialSignupTokenStore(new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    });

    @Test
    void returnsWhoLoggedInOnlyOnce() {
        String token = store.issue(Provider.KAKAO, "123", "kakao@example.com").value();

        PendingSocialSignup pending = store.consume(token).orElseThrow();
        assertThat(pending.provider()).isEqualTo(Provider.KAKAO);
        assertThat(pending.providerId()).isEqualTo("123");
        assertThat(pending.email()).isEqualTo("kakao@example.com");
        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void expiresAfterValidity() {
        String token = store.issue(Provider.KAKAO, "123", null).value();
        String stillValid = store.issue(Provider.KAKAO, "456", null).value();

        now.set(now.get().plus(SocialSignupTokenStore.VALIDITY).minusSeconds(1));
        assertThat(store.consume(stillValid)).isPresent();

        now.set(now.get().plusSeconds(1));
        assertThat(store.consume(token)).isEmpty();
    }

    @Test
    void issuesDifferentTokensEveryTime() {
        assertThat(store.issue(Provider.KAKAO, "123", null).value())
                .isNotEqualTo(store.issue(Provider.KAKAO, "123", null).value());
    }
}
