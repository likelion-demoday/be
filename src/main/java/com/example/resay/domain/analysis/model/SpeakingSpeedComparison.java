package com.example.resay.domain.analysis.model;

import java.math.BigDecimal;

// 화자 간 속도 비교
public record SpeakingSpeedComparison(
        SpeakerRole fasterSpeakerRole,
        SpeakerRole slowerSpeakerRole,
        BigDecimal percentDifference
) {

    public SpeakingSpeedComparison {
        if (fasterSpeakerRole == null || slowerSpeakerRole == null) {
            throw new IllegalArgumentException("비교할 화자 역할은 비어 있을 수 없습니다.");
        }
        if (fasterSpeakerRole == slowerSpeakerRole) {
            throw new IllegalArgumentException("서로 다른 화자만 비교할 수 있습니다.");
        }
        if (percentDifference == null || percentDifference.signum() <= 0) {
            throw new IllegalArgumentException("percentDifference는 양수여야 합니다.");
        }
    }
}
