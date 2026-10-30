package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.recording.service.RecordingService;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자가 보관 기간(3일)보다 먼저 대화를 지우는 기능
// 음성과 전사 원문은 지우고, 녹음은 숨김 처리한다 (보고서는 남는다. 보고서 삭제는 분석 쪽 기능)
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationDeletionService {

    private final RecordingRepository recordingRepository;
    private final RecordingService recordingService;
    private final TranscriptSegmentRepository transcriptSegmentRepository;

    @Transactional
    public void delete(Long recordingId, Long userId) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted())
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));

        recording.markDeleted();
        transcriptSegmentRepository.deleteByRecordingId(recordingId);
        // 음성 파일 삭제에 실패하면 기록하지 않고 남겨, 보관 기간 정리 스케줄러가 다시 지운다
        if (!recordingService.deleteAudio(recordingId)) {
            log.warn("대화 삭제 중 음성 파일을 지우지 못해 보관 기간 정리에서 다시 시도합니다. recordingId={}", recordingId);
        }
    }
}
