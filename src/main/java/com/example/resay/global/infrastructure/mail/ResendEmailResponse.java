package com.example.resay.global.infrastructure.mail;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ResendEmailResponse(String id) {
}
