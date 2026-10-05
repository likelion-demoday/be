package com.example.resay.global.infrastructure.image;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenAiImageRequest(
        String model,
        String prompt,
        int n,
        String size,
        String quality,
        @JsonProperty("output_format") String outputFormat,
        String background,
        String moderation
) {
}
