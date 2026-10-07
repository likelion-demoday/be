package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.event.AnalysisRequestedEvent;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisRequestedEventListener {

    private final ObjectProvider<AnalysisProcessor> analysisProcessorProvider;
    private final AnalysisService analysisService;

    @Async("analysisPipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnalysisRequestedEvent event) {
        try {
            AnalysisProcessor analysisProcessor = analysisProcessorProvider.getIfAvailable();
            if (analysisProcessor == null) {
                throw new IllegalStateException("분석 입력 조회 구현이 없습니다.");
            }
            analysisProcessor.process(event.recordingId());
        } catch (GeneralException exception) {
            if (exception.getErrorCode() == AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS) {
                log.info("중복 분석 요청을 건너뜁니다: recordingId={}", event.recordingId());
                return;
            }
            recordUnexpectedFailure(event.recordingId(), exception);
            log.error("분석 요청 처리 실패: recordingId={}", event.recordingId(), exception);
        } catch (RuntimeException exception) {
            recordUnexpectedFailure(event.recordingId(), exception);
            log.error("분석 요청 처리 실패: recordingId={}", event.recordingId(), exception);
        }
    }

    private void recordUnexpectedFailure(Long recordingId, RuntimeException originalException) {
        try {
            analysisService.recordFailure(recordingId, AnalysisFailureReason.PROCESSING_ERROR);
        } catch (RuntimeException failureException) {
            originalException.addSuppressed(failureException);
            log.error("분석 실패 상태 기록 실패: recordingId={}", recordingId, failureException);
        }
    }
}
