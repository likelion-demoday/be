package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.mail.AnalysisCompletedMailTemplate;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

// 분석이 끝난 녹음의 주인에게 분석 완료 메일을 보낸다.
// 메일 서비스 호출이 DB 트랜잭션을 붙잡지 않도록 이 메서드에는 트랜잭션을 두지 않고, 발송 기록만 따로 커밋한다
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisCompletedMailService {

    private final RecordingRepository recordingRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final UserRepository userRepository;
    private final NotificationLogService notificationLogService;
    private final AnalysisCompletedMailTemplate template;
    private final EmailSender emailSender;

    public void send(Long recordingId) {
        Recording recording = recordingRepository.findById(recordingId).orElse(null);
        // 분석이 끝나기 전에 사용자가 대화를 지웠으면 보내지 않는다
        if (recording == null || recording.isDeleted()) {
            return;
        }
        Long userId = recording.getUserId();
        NotificationSetting setting = notificationSettingRepository.findByUserId(userId).orElse(null);
        if (setting == null || !setting.isAnalysisEnabled() || !setting.hasEmail()) {
            return;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }

        Optional<Long> logId = claim(userId, recordingId);
        if (logId.isEmpty()) {
            log.info("이미 분석 완료 메일을 보낸 녹음입니다. recordingId={}", recordingId);
            return;
        }

        try {
            EmailSendResult result = emailSender.send(
                    template.create(setting.getEmail(), user.getNickname(), recordingId));
            notificationLogService.markResult(logId.get(), result);
        } catch (EmailSendException exception) {
            notificationLogService.markFailed(logId.get(), exception.getReason());
            log.warn("분석 완료 메일 발송 실패: recordingId={}, reason={}", recordingId, exception.getReason());
        }
    }

    private Optional<Long> claim(Long userId, Long recordingId) {
        try {
            return notificationLogService.claim(userId, NotificationType.ANALYSIS_COMPLETED, recordingId);
        } catch (DataIntegrityViolationException exception) {
            // 같은 녹음으로 동시에 들어온 다른 요청이 먼저 기록했다
            return Optional.empty();
        }
    }
}
