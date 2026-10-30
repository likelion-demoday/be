package com.example.resay.global.infrastructure.liner;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LinerErrorResponse(ErrorDetail error) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            String code,
            String message,
            Boolean retryable
    ) {
    }
}
