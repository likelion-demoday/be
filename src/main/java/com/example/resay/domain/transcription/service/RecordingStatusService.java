package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.code.RecordingErrorCode;
import com.example.resay.domain.recording.dto.RecordingStatusResponseDto;
import com.example.resay.domain.recording.dto.RecordingStep;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionStatus;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 프론트가 대기 화면에서 주기적으로 조회해 다음 화면으로 넘어갈 시점을 안다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordingStatusService {

    private final RecordingRepository recordingRepository;
    private final TranscriptionRepository transcriptionRepository;

    public RecordingStatusResponseDto getStatus(Long recordingId, Long userId) {
        Recording recording = recordingRepository.findByIdAndUserId(recordingId, userId)
                .filter(found -> !found.isDeleted()) // 대화를 삭제한 녹음은 없는 것으로 본다
                .orElseThrow(() -> new GeneralException(RecordingErrorCode.RECORDING_NOT_FOUND));
        boolean transcribed = recording.getStatus() == RecordingStatus.TRANSCRIBING
                && transcriptionRepository.findByRecordingId(recordingId)
                        .map(Transcription::getStatus)
                        .filter(status -> status == TranscriptionStatus.COMPLETED)
                        .isPresent();
        RecordingStep step = toStep(recording.getStatus(), transcribed);
        return new RecordingStatusResponseDto(
                recordingId,
                step,
                step == RecordingStep.FAILED ? recording.getFailureReason() : null
        );
    }

    static RecordingStep toStep(RecordingStatus status, boolean transcribed) {
        return switch (status) {
            case UPLOADED -> RecordingStep.UPLOADED;
            case TYPE_SELECTED -> RecordingStep.TYPE_SELECTED;
            // 결제 직후 전사 요청을 보내기 전 잠깐의 상태도 사용자에게는 전사 대기와 같다
            case PAYMENT_COMPLETED -> RecordingStep.TRANSCRIBING;
            case TRANSCRIBING -> transcribed ? RecordingStep.SPEAKER_SELECTION_REQUIRED : RecordingStep.TRANSCRIBING;
            case ANALYZING -> RecordingStep.ANALYZING;
            case COMPLETED -> RecordingStep.COMPLETED;
            case FAILED -> RecordingStep.FAILED;
        };
    }
}
