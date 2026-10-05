package com.example.resay.domain.analysis.model;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisReportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsPreviousReportWithoutSpeakerMetadata() throws Exception {
        AnalysisReport.RecordingInfo recordingInfo = objectMapper.readValue(
                """
                        {
                          "recordingId": 1,
                          "scenario": "FRIEND_DAILY",
                          "durationMs": 600000
                        }
                        """,
                AnalysisReport.RecordingInfo.class
        );

        assertThat(recordingInfo.speakers()).isEmpty();
    }
}
