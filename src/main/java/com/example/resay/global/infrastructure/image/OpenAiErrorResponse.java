package com.example.resay.global.infrastructure.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiErrorResponse(ErrorDetail error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            String message,
            String type,
            String code,
            String param
    ) {
    }
}
