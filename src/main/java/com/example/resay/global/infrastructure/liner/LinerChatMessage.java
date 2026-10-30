package com.example.resay.global.infrastructure.liner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinerChatMessage(
        String role,
        String content
) {
}
