package com.example.resay.domain.recording.event;

import com.example.resay.domain.recording.entity.RecordingFailureReason;

// 결제 후 단계(전사·화자 선택·분석)에서 녹음이 실패했음을 알린다 (크레딧 환급에 쓴다)
// 받는 쪽은 실패가 커밋된 뒤에 처리하도록 @TransactionalEventListener(AFTER_COMMIT)로 받는다
public record RecordingFailedEvent(
        Long recordingId,
        RecordingFailureReason failureReason
) {

    public RecordingFailedEvent {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
        if (failureReason == null) {
            throw new IllegalArgumentException("failureReason은 비어 있을 수 없습니다.");
        }
    }
}
