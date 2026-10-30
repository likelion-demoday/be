package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.config.AnalysisReadinessProperties;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(AnalysisReadinessProperties.class)
public class AnalysisReadinessValidator {

    private final AnalysisReadinessProperties properties;

    public AnalysisReadinessValidator(AnalysisReadinessProperties properties) {
        this.properties = properties;
    }

    public void validate(AnalysisSource source, ConversationMetrics metrics) {
        if (source == null || metrics == null) {
            throw new IllegalArgumentException("분석 입력과 정량 지표는 비어 있을 수 없습니다.");
        }

        Set<SpeakerRole> actualRoles = metrics.speakerMetrics().keySet();
        if (!actualRoles.equals(source.scenario().requiredRoles())) {
            throw new AnalysisReadinessException("두 화자의 정량 지표가 모두 필요합니다.");
        }

        long totalSpeakingDurationMs = metrics.speakerMetrics().values().stream()
                .mapToLong(speakerMetrics -> speakerMetrics.speakingDurationMs())
                .sum();
        if (totalSpeakingDurationMs < properties.minimumTotalSpeakingDuration().toMillis()) {
            throw new AnalysisReadinessException("전체 발화 시간이 분석 기준보다 짧습니다.");
        }

        for (SpeakerRole role : source.scenario().requiredRoles()) {
            var speakerMetrics = metrics.metricsFor(role);
            if (speakerMetrics.speakingDurationMs() < properties.minimumSpeakerSpeakingDuration().toMillis()) {
                throw new AnalysisReadinessException(role + " 화자의 발화 시간이 분석 기준보다 짧습니다.");
            }
            if (speakerMetrics.transcribedCharacterCount() < properties.minimumSpeakerCharacterCount()) {
                throw new AnalysisReadinessException(role + " 화자의 전사량이 분석 기준보다 적습니다.");
            }
            BigDecimal speakingRatio = speakerMetrics.speakingRatioPercent();
            if (speakingRatio.compareTo(properties.minimumSpeakerRatioPercent()) < 0) {
                throw new AnalysisReadinessException(role + " 화자의 발화 비율이 분석 기준보다 낮습니다.");
            }
        }
    }
}
