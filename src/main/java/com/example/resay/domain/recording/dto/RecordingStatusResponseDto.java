package com.example.resay.domain.recording.dto;

import com.example.resay.domain.recording.entity.RecordingFailureReason;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "녹음 처리 단계 및 실패 사유 응답")
public record RecordingStatusResponseDto(
        Long recordingId,
        RecordingStep step,
        @Schema(
                description = "실패 사유. 실패 상태가 아니면 null이며 ANALYSIS_FAILED는 레거시 값",
                example = "EXTERNAL_SERVICE_UNAVAILABLE"
        )
        RecordingFailureReason failureReason
) {
}
