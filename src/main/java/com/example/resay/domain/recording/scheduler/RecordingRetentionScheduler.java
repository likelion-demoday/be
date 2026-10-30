package com.example.resay.domain.recording.scheduler;

import com.example.resay.domain.recording.service.RecordingRetentionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class RecordingRetentionScheduler {

    private final RecordingRetentionService recordingRetentionService;

    // 결제 전 녹음(3시간) 기준이 가장 짧아 1시간마다 실행한다
    @Scheduled(cron = "${storage.retention.cron:0 0 * * * *}", zone = "Asia/Seoul")
    public void deleteExpiredRecordings() {
        LocalDateTime now = LocalDateTime.now();
        recordingRetentionService.deleteAbandonedRecordings(now);
        recordingRetentionService.deleteExpiredAudio(now);
    }
}
