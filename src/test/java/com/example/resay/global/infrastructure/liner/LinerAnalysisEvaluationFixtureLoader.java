package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

class LinerAnalysisEvaluationFixtureLoader {

    private static final Map<AnalysisScenario, String> DIRECTORY_BY_SCENARIO = Map.of(
            AnalysisScenario.FRIEND_DAILY, "friend-daily",
            AnalysisScenario.COUPLE_DAILY, "couple-daily",
            AnalysisScenario.COUPLE_CONFLICT, "couple-conflict",
            AnalysisScenario.PARENT_CHILD_CONFLICT, "parent-child-conflict"
    );

    private final ObjectMapper objectMapper;

    LinerAnalysisEvaluationFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    LinerAnalysisEvaluationFixture load(AnalysisScenario scenario) throws IOException {
        String directory = Objects.requireNonNull(
                DIRECTORY_BY_SCENARIO.get(scenario),
                "지원하지 않는 분석 시나리오입니다."
        );

        AnalysisSource correctedSource = read(
                "/mock/analysis/" + directory + ".json",
                AnalysisSource.class
        );
        AnalysisSource noisySource = read(
                "/analysis/evaluation/" + directory + "/noisy.json",
                AnalysisSource.class
        );
        LinerAnalysisEvaluationSpec evaluationSpec = read(
                "/analysis/evaluation/" + directory + "/evaluation.json",
                LinerAnalysisEvaluationSpec.class
        );

        return new LinerAnalysisEvaluationFixture(
                correctedSource,
                noisySource,
                evaluationSpec
        );
    }

    private <T> T read(String path, Class<T> type) throws IOException {
        try (InputStream inputStream = Objects.requireNonNull(
                getClass().getResourceAsStream(path),
                path + " 파일을 찾을 수 없습니다."
        )) {
            return objectMapper.readValue(inputStream, type);
        }
    }
}
