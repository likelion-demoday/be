package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AnalysisReadinessValidator {

    public void validate(AnalysisSource source, ConversationMetrics metrics) {
        if (source == null || metrics == null) {
            throw new IllegalArgumentException("분석 입력과 정량 지표는 비어 있을 수 없습니다.");
        }

        Set<SpeakerRole> actualRoles = metrics.speakerMetrics().keySet();
        if (!actualRoles.equals(source.scenario().requiredRoles())) {
            throw new AnalysisReadinessException("두 화자의 정량 지표가 모두 필요합니다.");
        }
    }
}
