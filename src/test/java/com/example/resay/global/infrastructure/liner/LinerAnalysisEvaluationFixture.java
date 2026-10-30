package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisSource;

record LinerAnalysisEvaluationFixture(
        AnalysisSource correctedSource,
        AnalysisSource noisySource,
        LinerAnalysisEvaluationSpec evaluationSpec
) {
}
