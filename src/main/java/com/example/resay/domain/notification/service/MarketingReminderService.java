package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.config.MarketingReminderProperties;
import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.mail.MarketingReminderMailTemplate;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.notification.repository.ReminderCandidate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

// 오래 쓰지 않은 사용자에게 마케팅 리마인드를 보낸다.
// 기준 시각(마지막 녹음, 없으면 가입)과 마지막 리마인드 중 늦은 시각으로부터 주기가 지난 사용자에게 한 통씩 보낸다.
// 다시 녹음하면 기준 시각이 새로 잡히고, 계속 쓰지 않으면 주기마다 한 통씩 간다
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketingReminderService {

    // 광고성 메일은 밤 9시 ~ 아침 8시에 보낼 수 없다 (정보통신망법). 발송 시각을 바꿔도 서버에서 막는다
    static final LocalTime SENDABLE_FROM = LocalTime.of(8, 0);
    static final LocalTime SENDABLE_UNTIL = LocalTime.of(21, 0);

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationLogService notificationLogService;
    private final MarketingReminderMailTemplate template;
    private final EmailSender emailSender;
    private final MarketingReminderProperties properties;

    // 보낸 통수를 돌려준다 (발송 설정이 없어 로그만 남긴 건 포함, 실패는 제외)
    public int sendDueReminders(LocalDateTime now) {
        if (!properties.enabled() || !isSendableTime(now)) {
            return 0;
        }
        List<ReminderCandidate> due = notificationSettingRepository
                .findReminderCandidates(NotificationType.MARKETING_REMINDER, NotificationLogStatus.FAILED)
                .stream()
                .filter(candidate -> isDue(candidate, now))
                // 오래 쓰지 않은 사용자부터 보내고, 하루 상한을 넘은 사용자는 다음 날 다시 대상이 된다
                .sorted(Comparator.comparing(MarketingReminderService::baseTime))
                .limit(properties.dailyLimit())
                .toList();

        int sent = 0;
        long startedAt = System.nanoTime();
        for (ReminderCandidate candidate : due) {
            if (sent > 0 && !pause(properties.sendInterval())) {
                // 서버가 꺼지는 중이다. 남은 대상은 다음 실행 때 보낸다
                log.info("서버 종료로 마케팅 리마인드를 멈춥니다.");
                break;
            }
            // 보내는 동안 흐른 시간만큼 더해, 발송이 길어져 밤 9시를 넘기면 멈춘다
            LocalDateTime current = now.plusNanos(System.nanoTime() - startedAt);
            if (!isSendableTime(current)) {
                log.info("광고 메일 발송 가능 시간이 지나 리마인드를 멈춥니다. 남은 대상은 다음 날 보냅니다.");
                break;
            }
            if (send(candidate, now)) {
                sent++;
            }
        }
        if (!due.isEmpty()) {
            log.info("마케팅 리마인드 발송: 대상 {}명, 발송 {}통", due.size(), sent);
        }
        return sent;
    }

    private boolean send(ReminderCandidate candidate, LocalDateTime now) {
        Long logId;
        try {
            logId = notificationLogService.start(candidate.userId(), NotificationType.MARKETING_REMINDER);
        } catch (RuntimeException exception) {
            // 기록 없이 보내면 다음 날 또 보내게 되므로 이 사용자는 건너뛴다
            log.error("마케팅 리마인드 기록 실패로 건너뜁니다: userId={}", candidate.userId(), exception);
            return false;
        }
        Long daysSinceLastRecording = candidate.lastRecordedAt() == null
                ? null
                : ChronoUnit.DAYS.between(candidate.lastRecordedAt().toLocalDate(), now.toLocalDate());
        EmailSendResult result;
        try {
            result = emailSender.send(template.create(
                    candidate.email(), candidate.nickname(), candidate.userId(), daysSinceLastRecording,
                    now.toLocalDate()));
        } catch (EmailSendException exception) {
            // 실패한 사용자는 다음 실행 때 다시 대상이 된다
            markFailed(logId, exception.getReason());
            log.warn("마케팅 리마인드 발송 실패: userId={}, reason={}", candidate.userId(), exception.getReason());
            return false;
        } catch (RuntimeException exception) {
            // 예상하지 못한 오류(요청 변환 실패 등)로 한 명 때문에 나머지 발송이 멈추지 않게 한다
            markFailed(logId, "unexpected_error");
            log.error("마케팅 리마인드 처리 실패: userId={}", candidate.userId(), exception);
            return false;
        }
        try {
            notificationLogService.markResult(logId, result);
        } catch (RuntimeException exception) {
            // 메일은 나갔다. PENDING으로 남아도 주기 동안 다시 보내지 않으므로 실패로 바꾸지 않는다
            log.error("마케팅 리마인드 결과 기록 실패: logId={}", logId, exception);
        }
        return true;
    }

    private void markFailed(Long logId, String reason) {
        try {
            notificationLogService.markFailed(logId, reason);
        } catch (RuntimeException exception) {
            // 기록하지 못하면 PENDING으로 남아 주기 동안 다시 보내지 않는다 (두 번 보내는 것보다 안전하다)
            log.error("마케팅 리마인드 실패 기록 실패: logId={}", logId, exception);
        }
    }

    private boolean isDue(ReminderCandidate candidate, LocalDateTime now) {
        LocalDateTime threshold = now.minusDays(properties.intervalDays());
        LocalDateTime latest = baseTime(candidate);
        if (candidate.lastRemindedAt() != null && candidate.lastRemindedAt().isAfter(latest)) {
            latest = candidate.lastRemindedAt();
        }
        return !latest.isAfter(threshold);
    }

    private static LocalDateTime baseTime(ReminderCandidate candidate) {
        return candidate.lastRecordedAt() != null ? candidate.lastRecordedAt() : candidate.signedUpAt();
    }

    static boolean isSendableTime(LocalDateTime time) {
        LocalTime clock = time.toLocalTime();
        return !clock.isBefore(SENDABLE_FROM) && clock.isBefore(SENDABLE_UNTIL);
    }

    // 쉬는 중에 서버 종료로 중단되면 false
    private static boolean pause(Duration interval) {
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        if (interval.isZero() || interval.isNegative()) {
            return true;
        }
        try {
            Thread.sleep(interval.toMillis());
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
