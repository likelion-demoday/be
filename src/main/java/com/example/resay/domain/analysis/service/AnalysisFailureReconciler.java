package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.service.AnalysisFailureRecordingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 분석 실패는 커밋됐지만 녹음 실패 이벤트를 반영하기 전에 서버가 종료된 건을 복구한다.
 * 녹음이 FAILED로 커밋되면 기존 RecordingFailedEvent 흐름이 크레딧을 환급하고,
 * 그 환급도 누락되면 CreditRefundReconciler가 다시 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalysisFailureReconciler {

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisFailureRecordingService analysisFailureRecordingService;

    @Scheduled(cron = "${analysis.failure-reconcile.cron:30 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        reconcileFailedAnalyses();
    }

    public int reconcileFailedAnalyses() {
        List<ConversationAnalysis> analyses =
                conversationAnalysisRepository.findFailedWithAnalyzingRecording();
        int recovered = 0;
        for (ConversationAnalysis analysis : analyses) {
            try {
                if (analysisFailureRecordingService.recordFailure(
                        analysis.getRecordingId(),
                        analysis.getFailureReason()
                )) {
                    recovered++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "분석 실패 녹음 반영에 실패했습니다. 다음 실행에서 다시 시도합니다. "
                                + "analysisId={}, recordingId={}",
                        analysis.getId(),
                        analysis.getRecordingId(),
                        exception
                );
            }
        }
        if (!analyses.isEmpty()) {
            log.info("분석 실패 녹음 정리: 대상 {}건, 복구 {}건", analyses.size(), recovered);
        }
        return recovered;
    }
}
