package com.example.resay.global.infrastructure.image;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "openai.image")
public record OpenAiImageProperties(
        String apiKey,
        String baseUrl,
        String model,
        String size,
        String quality,
        String outputFormat,
        String background,
        Duration connectTimeout,
        Duration readTimeout
) {

    private static final String DEFAULT_BASE_URL = "https://api.openai.com";
    private static final String DEFAULT_MODEL = "gpt-image-2.5-flare";
    private static final String DEFAULT_SIZE = "1024x1024";
    private static final String DEFAULT_QUALITY = "medium";
    private static final String DEFAULT_OUTPUT_FORMAT = "png";
    private static final String DEFAULT_BACKGROUND = "opaque";
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(180);
    private static final Set<String> QUALITIES = Set.of(
            "low", "medium", "high", "xhigh", "max", "auto"
    );
    private static final Set<String> OUTPUT_FORMATS = Set.of("png", "jpeg", "webp");
    private static final Set<String> BACKGROUNDS = Set.of("transparent", "opaque", "auto");

    public OpenAiImageProperties {
        baseUrl = hasText(baseUrl) ? baseUrl : DEFAULT_BASE_URL;
        model = hasText(model) ? model : DEFAULT_MODEL;
        size = hasText(size) ? size : DEFAULT_SIZE;
        quality = hasText(quality) ? quality : DEFAULT_QUALITY;
        outputFormat = hasText(outputFormat) ? outputFormat : DEFAULT_OUTPUT_FORMAT;
        background = hasText(background) ? background : DEFAULT_BACKGROUND;
        connectTimeout = connectTimeout != null ? connectTimeout : DEFAULT_CONNECT_TIMEOUT;
        readTimeout = readTimeout != null ? readTimeout : DEFAULT_READ_TIMEOUT;

        requireOneOf(quality, "quality", QUALITIES);
        requireOneOf(outputFormat, "outputFormat", OUTPUT_FORMATS);
        requireOneOf(background, "background", BACKGROUNDS);
        if (background.equals("transparent") && outputFormat.equals("jpeg")) {
            throw new IllegalArgumentException("투명 배경은 png 또는 webp 형식이어야 합니다.");
        }
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(readTimeout, "readTimeout");
    }

    public boolean isConfigured() {
        return hasText(apiKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireOneOf(String value, String fieldName, Set<String> allowed) {
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(fieldName + " 값이 올바르지 않습니다.");
        }
    }

    private static void requirePositive(Duration duration, String fieldName) {
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(fieldName + "은(는) 양수여야 합니다.");
        }
    }
}
