package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisFailureRecordingServiceTest {

    @Mock
    private RecordingService recordingService;

    @Test
    void keepsInsufficientSpeakerDataVisibleToUser() {
        AnalysisFailureRecordingService service = new AnalysisFailureRecordingService(recordingService);

        service.recordFailure(1L, AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA);

        then(recordingService).should().failAnalysis(
                1L,
                RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA
        );
    }

    @Test
    void hidesInternalFailureDetailsFromRecording() {
        AnalysisFailureRecordingService service = new AnalysisFailureRecordingService(recordingService);

        service.recordFailure(1L, AnalysisFailureReason.EXTERNAL_SERVICE_UNAVAILABLE);

        then(recordingService).should().failAnalysis(1L, RecordingFailureReason.ANALYSIS_FAILED);
    }
}
