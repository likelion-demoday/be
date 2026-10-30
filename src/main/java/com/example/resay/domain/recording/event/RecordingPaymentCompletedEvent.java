package com.example.resay.domain.recording.event;

// 녹음 결제가 끝났음을 알린다 (결제 트랜잭션이 커밋된 뒤 전사를 시작하는 데 쓴다)
public record RecordingPaymentCompletedEvent(Long recordingId) {

    public RecordingPaymentCompletedEvent {
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
    }
}
