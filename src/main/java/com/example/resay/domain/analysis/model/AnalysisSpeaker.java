package com.example.resay.domain.analysis.model;

import org.springframework.util.StringUtils;

public record AnalysisSpeaker(
        SpeakerRole speakerRole,
        String speakerName
) {

    public AnalysisSpeaker {
        if (speakerRole == null) {
            throw new IllegalArgumentException("speakerRole은 비어 있을 수 없습니다.");
        }
        if (!StringUtils.hasText(speakerName)) {
            throw new IllegalArgumentException("speakerName은 비어 있을 수 없습니다.");
        }
        speakerName = speakerName.trim();
    }
}
