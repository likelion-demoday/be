package com.example.resay.domain.transcription.scheduler;

import com.example.resay.domain.transcription.service.TranscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TranscriptionTimeoutScheduler {

    private final TranscriptionService transcriptionService;

    // 결과가 오지 않는 전사를 사용자가 오래 기다리지 않도록 10분마다 확인한다
    @Scheduled(cron = "${transcription.timeout.cron:0 */10 * * * *}", zone = "Asia/Seoul")
    public void failTimedOutTranscriptions() {
        List<Long> ids = transcriptionService.findTimedOutRecordingIds(LocalDateTime.now());
        for (Long id : ids) {
            try {
                transcriptionService.failTimedOut(id);
            } catch (RuntimeException e) {
                log.warn("전사 시간 초과 처리에 실패했습니다. 다음 실행에서 다시 시도합니다. recordingId={}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("전사 시간 초과 처리: {}건", ids.size());
        }
    }
}
