package com.example.resay.domain.transcription.service;

import com.example.resay.domain.analysis.event.AnalysisRequestedEvent;
import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.SpeakerMappingRequestDto;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionStatus;
import com.example.resay.domain.transcription.model.SpeakerLabels;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 전사가 끝난 녹음에서 사용자가 고른 본인 화자·상대 닉네임을 저장하고 분석을 요청한다
@Service
@RequiredArgsConstructor
public class SpeakerMappingService {

    private final RecordingRepository recordingRepository;
    private final TranscriptionRepository transcriptionRepository;
    private final TranscriptSegmentRepository transcriptSegmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void map(Long recordingId, Long userId, SpeakerMappingRequestDto request) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        if (recording.getStatus() != RecordingStatus.TRANSCRIBING) {
            throw new GeneralException(RecordingErrorCode.INVALID_STATUS_TRANSITION);
        }
        boolean transcribed = transcriptionRepository.findByRecordingId(recordingId)
                .map(Transcription::getStatus)
                .filter(status -> status == TranscriptionStatus.COMPLETED)
                .isPresent();
        if (!transcribed) {
            throw new GeneralException(RecordingErrorCode.TRANSCRIPTION_NOT_COMPLETED);
        }

        String selfLabel = SpeakerLabels.toStored(request.selfSpeakerLabel());
        if (selfLabel == null || !transcriptSegmentRepository.findSpeakerLabels(recordingId).contains(selfLabel)) {
            throw new GeneralException(RecordingErrorCode.INVALID_SPEAKER_MAPPING);
        }

        recording.mapSpeakers(selfLabel, request.partnerNickname(), request.selfRole());
        // 분석 쪽은 이 트랜잭션이 커밋된 뒤에 이벤트를 받아 분석을 시작한다
        eventPublisher.publishEvent(new AnalysisRequestedEvent(recordingId));
    }
}
