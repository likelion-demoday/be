package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;

record LinerAnalysisEvaluationSpec(
        AnalysisScenario scenario,
        String noiseLevel,
        List<Long> notableCorruptedSegmentIds,
        List<Long> meaningRiskSegmentIds,
        List<String> expectedTopics,
        List<ExpectedObservation> expectedObservations,
        List<String> forbiddenClaims
) {

    LinerAnalysisEvaluationSpec {
        notableCorruptedSegmentIds = copy(notableCorruptedSegmentIds);
        meaningRiskSegmentIds = copy(meaningRiskSegmentIds);
        expectedTopics = copy(expectedTopics);
        expectedObservations = copy(expectedObservations);
        forbiddenClaims = copy(forbiddenClaims);
    }

    record ExpectedObservation(
            String category,
            SpeakerRole speakerRole,
            List<Long> evidenceSegmentIds
    ) {

        ExpectedObservation {
            evidenceSegmentIds = copy(evidenceSegmentIds);
        }
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
