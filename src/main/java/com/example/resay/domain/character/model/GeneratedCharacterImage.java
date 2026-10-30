package com.example.resay.domain.character.model;

public record GeneratedCharacterImage(
        byte[] content,
        String mediaType,
        String model,
        String promptVersion
) {

    public GeneratedCharacterImage {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content는 비어 있을 수 없습니다.");
        }
        requireText(mediaType, "mediaType");
        requireText(model, "model");
        requireText(promptVersion, "promptVersion");
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }
}
