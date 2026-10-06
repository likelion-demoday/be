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

    // 결제했는데 전사가 시작되지 않은 녹음은 대신 전사를 시작한다 (5분마다 확인)
    // 결제 쪽은 커밋 후 직접 시작하고, 이 작업은 호출이 빠지거나 서버가 재시작된 경우의 안전장치다
    @Scheduled(cron = "${transcription.start-recovery.cron:0 */5 * * * *}", zone = "Asia/Seoul")
    public void startUnstartedTranscriptions() {
        List<Long> ids = transcriptionService.findTranscriptionNotStartedIds(LocalDateTime.now());
        for (Long id : ids) {
            transcriptionService.start(id);
        }
        if (!ids.isEmpty()) {
            log.info("시작되지 않은 전사 재시작: {}건", ids.size());
        }
    }

    // 전사가 끝났는데 화자를 고르지 않은 녹음은 3일 뒤 정리한다 (1시간마다 확인)
    @Scheduled(cron = "${transcription.speaker-selection-expiry.cron:0 30 * * * *}", zone = "Asia/Seoul")
    public void expireSpeakerSelections() {
        List<Long> ids = transcriptionService.findSpeakerSelectionExpiredRecordingIds(LocalDateTime.now());
        for (Long id : ids) {
            try {
                transcriptionService.expireSpeakerSelection(id);
            } catch (RuntimeException e) {
                log.warn("화자 선택 기한 만료 처리에 실패했습니다. 다음 실행에서 다시 시도합니다. recordingId={}", id, e);
            }
        }
        if (!ids.isEmpty()) {
            log.info("화자 선택 기한 만료 처리: {}건", ids.size());
        }
    }
}
