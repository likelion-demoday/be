package com.example.resay.global.infrastructure.liner;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinerPropertiesTest {

    @Test
    void appliesDefaults() {
        LinerProperties properties = new LinerProperties(null, null, null, null, null, null);

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("https://platform.liner.com");
        assertThat(properties.model()).isEqualTo("liner-mark-1.1");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(properties.retry().maxAttempts()).isEqualTo(3);
        assertThat(properties.retry().initialDelay()).isEqualTo(Duration.ofMillis(200));
        assertThat(properties.retry().maxDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void keepsConfiguredValues() {
        LinerProperties properties = new LinerProperties(
                "api-key",
                "http://localhost:8081",
                "test-model",
                Duration.ofSeconds(1),
                Duration.ofSeconds(10),
                new LinerProperties.Retry(2, Duration.ofMillis(10), Duration.ofMillis(20))
        );

        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("http://localhost:8081");
        assertThat(properties.model()).isEqualTo("test-model");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(properties.retry().maxAttempts()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new LinerProperties(
                "api-key",
                null,
                null,
                Duration.ZERO,
                Duration.ofSeconds(10),
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
