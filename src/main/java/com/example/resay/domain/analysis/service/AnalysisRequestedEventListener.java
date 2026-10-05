package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
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

    @Async("analysisPipelineExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AnalysisRequestedEvent event) {
        AnalysisProcessor analysisProcessor = analysisProcessorProvider.getIfAvailable();
        if (analysisProcessor == null) {
            log.error(
                    "분석 입력 조회 구현이 없어 분석 요청을 처리할 수 없습니다: recordingId={}",
                    event.recordingId()
            );
            return;
        }

        try {
            analysisProcessor.process(event.recordingId());
        } catch (GeneralException exception) {
            if (exception.getErrorCode() == AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS) {
                log.info("중복 분석 요청을 건너뜁니다: recordingId={}", event.recordingId());
                return;
            }
            log.error("분석 요청 처리 실패: recordingId={}", event.recordingId(), exception);
        } catch (RuntimeException exception) {
            log.error("분석 요청 처리 실패: recordingId={}", event.recordingId(), exception);
        }
    }
}
