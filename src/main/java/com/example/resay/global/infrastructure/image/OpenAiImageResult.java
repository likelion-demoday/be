package com.example.resay.global.infrastructure.image;

public record OpenAiImageResult(
        String requestId,
        OpenAiImageResponse response
) {

    public String base64Image() {
        return response.data().get(0).base64Json();
    }
}
