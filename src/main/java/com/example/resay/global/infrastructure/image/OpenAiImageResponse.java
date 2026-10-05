package com.example.resay.global.infrastructure.image;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiImageResponse(
        long created,
        List<ImageData> data,
        @JsonProperty("output_format") String outputFormat,
        String quality,
        String size
) {

    public OpenAiImageResponse {
        data = data == null ? List.of() : List.copyOf(data);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImageData(@JsonProperty("b64_json") String base64Json) {
    }
}
