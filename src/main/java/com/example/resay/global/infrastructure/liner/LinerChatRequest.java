package com.example.resay.global.infrastructure.liner;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record LinerChatRequest(
        String model,
        List<LinerChatMessage> messages,
        boolean stream,
        @JsonProperty("max_completion_tokens") Integer maxCompletionTokens,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        @JsonProperty("response_format") Map<String, Object> responseFormat
) {

    public LinerChatRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
        responseFormat = responseFormat == null ? Map.of() : Map.copyOf(responseFormat);
    }
}
