package com.example.resay.domain.analysis.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisResultTest {

    @Test
    void createsAnalysisResult() {
        AnalysisResult result = AnalysisResult.create(
                1L,
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        );

        assertThat(result.getAnalysisId()).isEqualTo(1L);
        assertThat(result.getResultJson()).isEqualTo("{\"summary\":\"대화 요약\"}");
        assertThat(result.getModelName()).isEqualTo("liner-mark-1.1");
        assertThat(result.getPromptVersion()).isEqualTo("v1");
        assertThat(result.getSchemaVersion()).isEqualTo("v1");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void rejectsInvalidAnalysisId(Long analysisId) {
        assertThatThrownBy(() -> AnalysisResult.create(
                analysisId,
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankResultJson(String resultJson) {
        assertThatThrownBy(() -> AnalysisResult.create(
                1L,
                resultJson,
                "liner-mark-1.1",
                "v1",
                "v1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankModelName(String modelName) {
        assertThatThrownBy(() -> AnalysisResult.create(
                1L,
                "{\"summary\":\"대화 요약\"}",
                modelName,
                "v1",
                "v1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTooLongModelName() {
        assertThatThrownBy(() -> AnalysisResult.create(
                1L,
                "{\"summary\":\"대화 요약\"}",
                "a".repeat(101),
                "v1",
                "v1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankPromptVersion(String promptVersion) {
        assertThatThrownBy(() -> AnalysisResult.create(
                1L,
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                promptVersion,
                "v1"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " "})
    void rejectsBlankSchemaVersion(String schemaVersion) {
        assertThatThrownBy(() -> AnalysisResult.create(
                1L,
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                schemaVersion
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
