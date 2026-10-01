package com.example.resay.domain.analysis.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// 두 화자의 전체 결과
public record ConversationMetrics(
        Map<SpeakerRole, SpeakerMetrics> speakerMetrics
) {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    public ConversationMetrics {
        if (speakerMetrics == null || speakerMetrics.size() != 2) {
            throw new IllegalArgumentException("두 화자의 정량 지표가 필요합니다.");
        }
        for (Map.Entry<SpeakerRole, SpeakerMetrics> entry : speakerMetrics.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getKey() != entry.getValue().speakerRole()) {
                throw new IllegalArgumentException("화자 역할과 정량 지표가 일치하지 않습니다.");
            }
        }
        speakerMetrics = Map.copyOf(speakerMetrics);
    }

    public SpeakerMetrics metricsFor(SpeakerRole speakerRole) {
        SpeakerMetrics metrics = speakerMetrics.get(speakerRole);
        if (metrics == null) {
            throw new IllegalArgumentException("요청한 화자의 정량 지표가 없습니다.");
        }
        return metrics;
    }

    public Optional<SpeakingSpeedComparison> speakingSpeedComparison() {
        List<SpeakerMetrics> metrics = List.copyOf(speakerMetrics.values());
        SpeakerMetrics first = metrics.get(0);
        SpeakerMetrics second = metrics.get(1);
        int comparison = first.charactersPerMinute().compareTo(second.charactersPerMinute());

        if (comparison == 0) {
            return Optional.empty();
        }

        SpeakerMetrics faster = comparison > 0 ? first : second;
        SpeakerMetrics slower = comparison > 0 ? second : first;
        if (slower.charactersPerMinute().signum() == 0) {
            return Optional.empty();
        }

        BigDecimal percentDifference = faster.charactersPerMinute()
                .subtract(slower.charactersPerMinute())
                .multiply(ONE_HUNDRED)
                .divide(slower.charactersPerMinute(), SCALE, RoundingMode.HALF_UP);
        if (percentDifference.signum() == 0) {
            return Optional.empty();
        }

        return Optional.of(new SpeakingSpeedComparison(
                faster.speakerRole(),
                slower.speakerRole(),
                percentDifference
        ));
    }
}
