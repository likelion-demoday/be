package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnalysisStallService {

    private static final AnalysisFailureReason FAILURE_REASON =
            AnalysisFailureReason.PROCESSING_ERROR;

    private final ConversationAnalysisRepository conversationAnalysisRepository;
    private final RecordingRepository recordingRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public boolean failStalledAnalysis(
            Long analysisId,
            Long recordingId,
            LocalDateTime cutoff,
            LocalDateTime now
    ) {
        int updated = conversationAnalysisRepository.markStalledFailed(
                analysisId,
                recordingId,
                cutoff,
                now
        );
        if (updated == 0) {
            return false;
        }
        eventPublisher.publishEvent(new AnalysisFailedEvent(recordingId, FAILURE_REASON));
        return true;
    }

    /**
     * 화자 선택 커밋 뒤 분석 시작 이벤트를 받기 전에 서버가 종료된 건을 실패 처리한다.
     * 녹음을 잠근 뒤 상태와 수정 시각을 다시 확인해 새로 진행된 작업을 건드리지 않는다.
     */
    @Transactional
    public boolean failStalledRecordingWithoutAnalysis(
            Long recordingId,
            LocalDateTime cutoff
    ) {
        Recording recording = recordingRepository.findByIdForUpdate(recordingId)
                .orElse(null);
        if (recording == null
                || recording.getStatus() != RecordingStatus.ANALYZING
                || !recording.getUpdatedAt().isBefore(cutoff)
                || conversationAnalysisRepository.existsByRecordingId(recordingId)) {
            return false;
        }

        ConversationAnalysis analysis = ConversationAnalysis.start(recordingId);
        analysis.fail(FAILURE_REASON);
        conversationAnalysisRepository.saveAndFlush(analysis);
        eventPublisher.publishEvent(new AnalysisFailedEvent(recordingId, FAILURE_REASON));
        return true;
    }
}
