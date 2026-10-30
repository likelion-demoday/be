package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisFailureRecordingServiceTest {

    @Mock
    private RecordingService recordingService;

    @ParameterizedTest
    @CsvSource({
            "INSUFFICIENT_SPEAKER_DATA, INSUFFICIENT_SPEAKER_DATA",
            "EXTERNAL_SERVICE_UNAVAILABLE, EXTERNAL_SERVICE_UNAVAILABLE",
            "INVALID_ANALYSIS_RESPONSE, INVALID_ANALYSIS_RESPONSE",
            "PROCESSING_ERROR, PROCESSING_ERROR"
    })
    void preservesPublicAnalysisFailureReason(
            AnalysisFailureReason analysisReason,
            RecordingFailureReason recordingReason
    ) {
        AnalysisFailureRecordingService service = new AnalysisFailureRecordingService(recordingService);

        service.recordFailure(1L, analysisReason);

        then(recordingService).should().failAnalysis(1L, recordingReason);
    }
}
