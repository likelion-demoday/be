package com.example.resay.global.infrastructure.image;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiImagePropertiesTest {

    @Test
    void appliesDefaults() {
        OpenAiImageProperties properties = new OpenAiImageProperties(
                null, null, null, null, null, null, null, null, null
        );

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("https://api.openai.com");
        assertThat(properties.model()).isEqualTo("gpt-image-2.5-flare");
        assertThat(properties.size()).isEqualTo("1024x1024");
        assertThat(properties.quality()).isEqualTo("medium");
        assertThat(properties.outputFormat()).isEqualTo("png");
        assertThat(properties.background()).isEqualTo("opaque");
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(3));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(180));
    }

    @Test
    void rejectsTransparentJpeg() {
        assertThatThrownBy(() -> new OpenAiImageProperties(
                "key",
                null,
                null,
                null,
                null,
                "jpeg",
                "transparent",
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedQuality() {
        assertThatThrownBy(() -> new OpenAiImageProperties(
                "key",
                null,
                null,
                null,
                "ultra",
                null,
                null,
                null,
                null
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
