package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.entity.NotificationLog;
import com.example.resay.domain.notification.entity.NotificationLogStatus;
import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.entity.NotificationType;
import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationLogRepository;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@Transactional
class AnalysisCompletedMailServiceTest {

    @Autowired
    private AnalysisCompletedMailService analysisCompletedMailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @MockitoBean
    private EmailSender emailSender;

    private User user;
    private Recording recording;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("login@example.com", "encoded", "지현"));
        user.agreeToTerms(LocalDateTime.now(), false);
        recording = recordingRepository.saveAndFlush(Recording.create(user.getId(), "mail-test.m4a", 600));
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg-1"));
    }

    private void saveSetting(String email, boolean analysisEnabled) {
        NotificationSetting setting = NotificationSetting.createDefault(user.getId());
        setting.changeEmail(email);
        setting.changeAnalysisEnabled(analysisEnabled);
        notificationSettingRepository.saveAndFlush(setting);
    }

    private NotificationLog findLog() {
        return notificationLogRepository
                .findByTypeAndTargetId(NotificationType.ANALYSIS_COMPLETED, recording.getId())
                .orElseThrow();
    }

    @Test
    void 분석_완료_알림을_켠_사용자의_알림_이메일로_보내고_기록한다() {
        saveSetting("alert@example.com", true);

        analysisCompletedMailService.send(recording.getId());

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        assertThat(captor.getValue().to()).isEqualTo("alert@example.com");
        assertThat(captor.getValue().html())
                .contains("지현님")
                .contains("https://resay.site/report/" + recording.getId());

        NotificationLog log = findLog();
        assertThat(log.getStatus()).isEqualTo(NotificationLogStatus.SENT);
        assertThat(log.getUserId()).isEqualTo(user.getId());
        assertThat(log.getProviderMessageId()).isEqualTo("msg-1");
    }

    @Test
    void 알림을_끈_사용자에게는_보내지_않고_기록도_남기지_않는다() {
        saveSetting("alert@example.com", false);

        analysisCompletedMailService.send(recording.getId());

        verify(emailSender, never()).send(any());
        assertThat(notificationLogRepository.count()).isZero();
    }

    @Test
    void 알림_이메일이_없으면_보내지_않는다() {
        saveSetting(null, true);

        analysisCompletedMailService.send(recording.getId());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 알림_설정을_저장한_적이_없으면_보내지_않는다() {
        analysisCompletedMailService.send(recording.getId());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 분석이_끝나기_전에_지운_대화는_보내지_않는다() {
        saveSetting("alert@example.com", true);
        ReflectionTestUtils.setField(recording, "deletedAt", LocalDateTime.now());
        recordingRepository.saveAndFlush(recording);

        analysisCompletedMailService.send(recording.getId());

        verify(emailSender, never()).send(any());
    }

    @Test
    void 같은_녹음으로는_한_번만_보낸다() {
        saveSetting("alert@example.com", true);

        analysisCompletedMailService.send(recording.getId());
        analysisCompletedMailService.send(recording.getId());

        verify(emailSender, times(1)).send(any());
        assertThat(notificationLogRepository.count()).isEqualTo(1);
    }

    @Test
    void 발송에_실패하면_사유를_기록하고_예외를_밖으로_던지지_않는다() {
        saveSetting("alert@example.com", true);
        given(emailSender.send(any())).willThrow(new EmailSendException("HTTP_403 validation_error", null));

        assertThatCode(() -> analysisCompletedMailService.send(recording.getId())).doesNotThrowAnyException();

        NotificationLog log = findLog();
        assertThat(log.getStatus()).isEqualTo(NotificationLogStatus.FAILED);
        assertThat(log.getFailureReason()).isEqualTo("HTTP_403 validation_error");
    }

    @Test
    void 발송_설정이_없어_보내지_않았으면_그렇게_기록한다() {
        saveSetting("alert@example.com", true);
        given(emailSender.send(any())).willReturn(EmailSendResult.notConfigured());

        analysisCompletedMailService.send(recording.getId());

        assertThat(findLog().getStatus()).isEqualTo(NotificationLogStatus.NOT_CONFIGURED);
    }

    @Test
    void 없는_녹음이면_아무것도_하지_않는다() {
        analysisCompletedMailService.send(Long.MAX_VALUE);

        verify(emailSender, never()).send(any());
    }
}
