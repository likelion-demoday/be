package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;

record LinerAnalysisQualityEvaluation(
        AnalysisScenario scenario,
        TranscriptVariant transcriptVariant,
        List<String> expectedTopics,
        List<QualitativeAnalysis.TimelineItem> actualTimeline,
        List<ObservationEvaluation> observations,
        List<String> detectedForbiddenClaims,
        List<Long> referencedCorruptedSegmentIds,
        List<Long> referencedMeaningRiskSegmentIds
) {

    enum TranscriptVariant {
        CORRECTED,
        NOISY
    }

    record ObservationEvaluation(
            String category,
            SpeakerRole speakerRole,
            boolean found,
            List<Long> expectedEvidenceSegmentIds,
            List<Long> actualEvidenceSegmentIds,
            List<Long> overlappingEvidenceSegmentIds
    ) {
    }
}
