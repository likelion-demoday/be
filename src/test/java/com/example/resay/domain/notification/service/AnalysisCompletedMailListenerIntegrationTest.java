package com.example.resay.domain.notification.service;

import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.notification.entity.NotificationSetting;
import com.example.resay.domain.notification.port.EmailSendException;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationLogRepository;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

// 실제 분석 저장처럼 트랜잭션 안에서 이벤트를 발행하고, 커밋된 뒤 다른 스레드에서 메일이 나가는지 확인한다
@SpringBootTest
class AnalysisCompletedMailListenerIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    private NotificationSetting setting;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.createLocal("listener-mail@example.com", "encoded", "지현"));
        recording = Recording.create(user.getId(), "listener-mail.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.ANALYZING);
        recording = recordingRepository.save(recording);
        setting = NotificationSetting.createDefault(user.getId());
        setting.changeEmail("alert@example.com");
        setting.changeAnalysisEnabled(true);
        setting = notificationSettingRepository.save(setting);
    }

    @AfterEach
    void tearDown() {
        notificationLogRepository.deleteAll();
        notificationSettingRepository.deleteById(setting.getId());
        recordingRepository.deleteById(recording.getId());
        userRepository.deleteById(user.getId());
    }

    private void publishInTransaction(boolean rollback) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(new AnalysisCompletedEvent(1L, recording.getId()));
            if (rollback) {
                status.setRollbackOnly();
            }
        });
    }

    @Test
    void 분석_저장이_커밋되면_메일을_보낸다() {
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg-1"));

        publishInTransaction(false);

        verify(emailSender, timeout(5000)).send(any());
    }

    @Test
    void 분석_저장이_롤백되면_메일을_보내지_않는다() {
        publishInTransaction(true);

        verify(emailSender, after(500).never()).send(any());
    }

    @Test
    void 메일이_실패해도_녹음은_분석_완료로_바뀐다() {
        given(emailSender.send(any())).willThrow(new EmailSendException("transport_error", null));

        publishInTransaction(false);

        verify(emailSender, timeout(5000)).send(any());
        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getStatus())
                .isEqualTo(RecordingStatus.COMPLETED);
    }
}
