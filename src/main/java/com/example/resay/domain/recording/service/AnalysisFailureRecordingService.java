package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AnalysisFailureRecordingService {

    private final RecordingService recordingService;

    public boolean recordFailure(Long recordingId, AnalysisFailureReason reason) {
        return recordingService.failAnalysis(recordingId, toRecordingReason(reason));
    }

    // 분석에서 정제한 공개용 사유만 녹음에 저장하고 내부 오류 분류는 노출하지 않는다.
    private RecordingFailureReason toRecordingReason(AnalysisFailureReason reason) {
        return switch (reason) {
            case INSUFFICIENT_SPEAKER_DATA -> RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA;
            case EXTERNAL_SERVICE_UNAVAILABLE -> RecordingFailureReason.EXTERNAL_SERVICE_UNAVAILABLE;
            case INVALID_ANALYSIS_RESPONSE -> RecordingFailureReason.INVALID_ANALYSIS_RESPONSE;
            case PROCESSING_ERROR -> RecordingFailureReason.PROCESSING_ERROR;
        };
    }
}
