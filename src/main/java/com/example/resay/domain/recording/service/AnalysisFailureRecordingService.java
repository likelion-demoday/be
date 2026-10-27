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

    // 상세 분석 실패 분류는 분석에 남기고, 녹음에는 사용자 안내용 사유만 저장한다.
    private RecordingFailureReason toRecordingReason(AnalysisFailureReason reason) {
        return switch (reason) {
            case INSUFFICIENT_SPEAKER_DATA -> RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA;
            case EXTERNAL_SERVICE_UNAVAILABLE, INVALID_ANALYSIS_RESPONSE, PROCESSING_ERROR ->
                    RecordingFailureReason.ANALYSIS_FAILED;
        };
    }
}
