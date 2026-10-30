package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.config.AnalysisStallProperties;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 서버 재시작 등으로 더 이상 진행되지 않는 분석과 분석 전환 녹음을 실패 처리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AnalysisStallProperties.class)
public class AnalysisStallReconciler {

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final AnalysisStallService analysisStallService;
    private final AnalysisStallProperties properties;

    @Scheduled(cron = "${analysis.stall.reconcile-cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void reconcile() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minus(properties.timeout());
        List<ConversationAnalysis> analyses =
                conversationAnalysisRepository.findStalledAnalyzing(cutoff);
        List<Long> recordingsWithoutAnalysis = conversationAnalysisRepository
                .findStalledAnalyzingRecordingIdsWithoutAnalysis(cutoff);

        int failedAnalyses = 0;
        for (ConversationAnalysis analysis : analyses) {
            try {
                if (analysisStallService.failStalledAnalysis(
                        analysis.getId(),
                        analysis.getRecordingId(),
                        cutoff,
                        now
                )) {
                    failedAnalyses++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "멈춘 분석 실패 처리에 실패했습니다. 다음 실행에서 다시 시도합니다. "
                                + "analysisId={}, recordingId={}",
                        analysis.getId(),
                        analysis.getRecordingId(),
                        exception
                );
            }
        }

        int failedWithoutAnalysis = 0;
        for (Long recordingId : recordingsWithoutAnalysis) {
            try {
                if (analysisStallService.failStalledRecordingWithoutAnalysis(
                        recordingId,
                        cutoff
                )) {
                    failedWithoutAnalysis++;
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "시작되지 않은 분석 실패 처리에 실패했습니다. 다음 실행에서 다시 시도합니다. "
                                + "recordingId={}",
                        recordingId,
                        exception
                );
            }
        }

        int targets = analyses.size() + recordingsWithoutAnalysis.size();
        if (targets > 0) {
            log.info(
                    "멈춘 분석 정리: 대상 {}건, 분석 실패 {}건, 미시작 실패 {}건",
                    targets,
                    failedAnalyses,
                    failedWithoutAnalysis
            );
        }
    }
}
