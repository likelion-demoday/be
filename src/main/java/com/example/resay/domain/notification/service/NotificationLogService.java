package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.entity.NotificationLog;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// 발송 기록은 메일을 보내기 전 · 후로 나눠 각각 커밋한다.
// 보내기 전에 먼저 기록해 두면, 같은 대상으로 동시에 들어온 요청 중 하나만 유니크 제약을 통과해 메일이 두 번 나가지 않는다
@Service
@RequiredArgsConstructor
public class NotificationLogService {

    private final NotificationLogRepository notificationLogRepository;

    // 이미 기록이 있으면 비어 있는 값을 돌려준다. 동시에 들어와 유니크 제약에 걸리면 DataIntegrityViolationException
    @Transactional
    public Optional<Long> claim(Long userId, NotificationType type, Long targetId) {
        if (notificationLogRepository.existsByTypeAndTargetId(type, targetId)) {
            return Optional.empty();
        }
        NotificationLog log = notificationLogRepository.saveAndFlush(NotificationLog.pending(userId, type, targetId));
        return Optional.of(log.getId());
    }

    @Transactional
    public void markResult(Long logId, EmailSendResult result) {
        NotificationLog log = notificationLogRepository.findById(logId).orElseThrow();
        if (result.sent()) {
            log.markSent(result.messageId());
        } else {
            log.markNotConfigured();
        }
    }

    @Transactional
    public void markFailed(Long logId, String reason) {
        notificationLogRepository.findById(logId).orElseThrow().markFailed(reason);
    }
}
