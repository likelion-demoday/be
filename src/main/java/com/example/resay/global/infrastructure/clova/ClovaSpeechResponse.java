package com.example.resay.global.infrastructure.clova;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ClovaSpeechResponse(
        String result,
        String message,
        String token,
        List<ClovaSegment> segments,
        List<ClovaSpeaker> speakers
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClovaSegment(
            long start,
            long end,
            String text,
            double confidence,
            ClovaSpeakerRef speaker
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClovaSpeakerRef(
            String label,
            String name
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClovaSpeaker(
            String label,
            String name
    ) {
    }
}