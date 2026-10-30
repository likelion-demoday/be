package com.example.resay.domain.analysis.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisModelResultTest {

    @Test
    void createsAnalysisModelResult() {
        AnalysisModelResult result = new AnalysisModelResult(
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        );

        assertThat(result.resultJson()).isEqualTo("{\"summary\":\"대화 요약\"}");
        assertThat(result.modelName()).isEqualTo("liner-mark-1.1");
    }

    @Test
    void rejectsBlankResultJson() {
        assertThatThrownBy(() -> new AnalysisModelResult(" ", "liner-mark-1.1", "v1", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
