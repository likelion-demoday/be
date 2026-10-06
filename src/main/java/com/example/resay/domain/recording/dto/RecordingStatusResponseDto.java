package com.example.resay.domain.recording.dto;

import com.example.resay.domain.recording.entity.RecordingFailureReason;

public record RecordingStatusResponseDto(
        Long recordingId,
        RecordingStep step,
        // 실패했을 때만 값이 있다
        RecordingFailureReason failureReason
) {
}
