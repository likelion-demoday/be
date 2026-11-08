package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.code.NotificationErrorCode;
import com.example.resay.domain.notification.config.ServiceAnnouncementProperties;
import com.example.resay.domain.notification.dto.DailyQuotaExceededDetail;
import com.example.resay.domain.notification.dto.ServiceAnnouncementRequestDto;
import com.example.resay.domain.notification.dto.ServiceAnnouncementResponseDto;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.mail.ServiceAnnouncementMailTemplate;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.notification.repository.ServiceAnnouncementTarget;
import com.example.resay.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

// 관리자가 쓴 서비스 알림을 서비스 알림을 켠 사용자 모두에게 보낸다.
// 대상이 오늘 남은 발송 가능 수보다 많으면 일부만 가지 않도록 한 통도 보내지 않고 거절한다.
// 메일 서비스 호출이 DB 트랜잭션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않고, 발송 기록만 따로 커밋한다
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceAnnouncementService {

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationQuotaService notificationQuotaService;
    private final NotificationLogService notificationLogService;
    private final ServiceAnnouncementMailTemplate template;
    private final EmailSender emailSender;
    private final ServiceAnnouncementProperties properties;

    public ServiceAnnouncementResponseDto send(ServiceAnnouncementRequestDto request) {
        List<ServiceAnnouncementTarget> targets = notificationSettingRepository.findServiceAnnouncementTargets();
        int remaining = notificationQuotaService.remainingToday(LocalDateTime.now());
        if (targets.size() > remaining) {
            throw new GeneralException(
                    NotificationErrorCode.DAILY_QUOTA_EXCEEDED, new DailyQuotaExceededDetail(targets.size(), remaining));
        }

        int sent = 0;
        int failed = 0;
        for (ServiceAnnouncementTarget target : targets) {
            if (sent + failed > 0 && !pause(properties.sendInterval())) {
                // 서버가 꺼지는 중이다. 쉬지 않고 남은 대상에게 몰아 보내다 실패로 남기지 않게 여기서 멈춘다
                log.warn("서버 종료로 서비스 알림 발송을 멈춥니다. 보내지 못한 대상 {}명",
                        targets.size() - sent - failed);
                break;
            }
            if (send(target, request)) {
                sent++;
            } else {
                failed++;
            }
        }
        log.info("서비스 알림 발송: 대상 {}명, 성공 {}통, 실패 {}통", targets.size(), sent, failed);
        return new ServiceAnnouncementResponseDto(targets.size(), sent, failed);
    }

    private boolean send(ServiceAnnouncementTarget target, ServiceAnnouncementRequestDto request) {
        Long logId;
        try {
            logId = notificationLogService.start(target.userId(), NotificationType.SERVICE_ANNOUNCEMENT);
        } catch (RuntimeException exception) {
            log.error("서비스 알림 기록 실패로 건너뜁니다: userId={}", target.userId(), exception);
            return false;
        }
        EmailSendResult result;
        try {
            result = emailSender.send(template.create(target.email(), target.userId(), request.subject(), request.body()));
        } catch (EmailSendException exception) {
            markFailed(logId, exception.getReason());
            log.warn("서비스 알림 발송 실패: userId={}, reason={}", target.userId(), exception.getReason());
            return false;
        } catch (RuntimeException exception) {
            markFailed(logId, "unexpected_error");
            log.error("서비스 알림 처리 실패: userId={}", target.userId(), exception);
            return false;
        }
        try {
            notificationLogService.markResult(logId, result);
        } catch (RuntimeException exception) {
            // 메일은 나갔으므로 성공으로 센다
            log.error("서비스 알림 결과 기록 실패: logId={}", logId, exception);
        }
        return true;
    }

    private void markFailed(Long logId, String reason) {
        try {
            notificationLogService.markFailed(logId, reason);
        } catch (RuntimeException exception) {
            log.error("서비스 알림 실패 기록 실패: logId={}", logId, exception);
        }
    }

    // 쉬는 중에(또는 그 전에) 서버 종료로 중단되면 false
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
