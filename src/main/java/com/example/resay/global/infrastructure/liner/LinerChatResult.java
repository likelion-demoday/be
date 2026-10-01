package com.example.resay.global.infrastructure.liner;

public record LinerChatResult(
        String requestId,
        LinerChatResponse response
) {

    public String content() {
        return response.choices().get(0).message().content();
    }
}
