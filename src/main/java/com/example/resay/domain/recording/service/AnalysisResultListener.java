package com.example.resay.domain.recording.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 분석 결과를 녹음 상태에 반영한다 (완료·실패 시각이 음성 3일 보관의 기준이 된다)
// 분석 저장이 커밋된 뒤 별도 트랜잭션으로 처리해, 여기서 실패해도 분석 결과는 남는다
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisResultListener {

    private final RecordingService recordingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCompleted(AnalysisCompletedEvent event) {
        if (!recordingService.completeAnalysis(event.recordingId())) {
            log.info("분석 완료를 반영할 녹음이 없습니다. recordingId={}", event.recordingId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFailed(AnalysisFailedEvent event) {
        if (!recordingService.failAnalysis(event.recordingId(), toRecordingReason(event.failureReason()))) {
            log.info("분석 실패를 반영할 녹음이 없습니다. recordingId={}", event.recordingId());
        }
    }

    // 발화 부족은 프론트가 녹음 상태 안내를 따로 보여줄 수 있도록 구분해서 저장한다
    private RecordingFailureReason toRecordingReason(AnalysisFailureReason reason) {
        return switch (reason) {
            case INSUFFICIENT_SPEAKER_DATA -> RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA;
            case PROCESSING_ERROR -> RecordingFailureReason.ANALYSIS_FAILED;
        };
    }
}
