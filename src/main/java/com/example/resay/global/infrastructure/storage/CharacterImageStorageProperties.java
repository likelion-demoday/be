package com.example.resay.global.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.character")
public record CharacterImageStorageProperties(String basePath) {

    public CharacterImageStorageProperties {
        if (basePath == null || basePath.isBlank()) {
            throw new IllegalArgumentException("캐릭터 이미지 저장 경로가 필요합니다.");
        }
    }
}
