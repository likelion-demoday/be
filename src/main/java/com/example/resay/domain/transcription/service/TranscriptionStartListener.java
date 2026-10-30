package com.example.resay.domain.transcription.service;

import com.example.resay.domain.recording.event.RecordingPaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 결제가 커밋된 뒤 전사를 시작한다 (전사 요청은 별도 스레드에서 실행되어 결제 응답을 붙잡지 않는다)
// 이벤트가 유실되면 결제 후 5분이 지난 녹음을 스케줄러가 대신 시작한다
@Component
@RequiredArgsConstructor
public class TranscriptionStartListener {

    private final TranscriptionService transcriptionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(RecordingPaymentCompletedEvent event) {
        transcriptionService.start(event.recordingId());
    }
}
