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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 가입 · 녹음 · 리마인드 시각은 자동 기록되어 바꿀 수 없으므로 SQL로 과거 시각을 넣는다
@SpringBootTest
@Transactional
class MarketingReminderServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 19, 0);
    private static final String EMAIL = "reminder-target@example.com";

    @Autowired
    private MarketingReminderService marketingReminderService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private NotificationSettingRepository notificationSettingRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmailSender emailSender;

    private User user;

    @BeforeEach
    void setUp() {
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg-1"));
        user = createUser("reminder-login@example.com", EMAIL, true, NOW.minusDays(30));
    }

    private User createUser(String loginEmail, String alertEmail, boolean marketing, LocalDateTime signedUpAt) {
        User created = userRepository.saveAndFlush(User.createLocal(loginEmail, "encoded", "지현"));
        created.agreeToTerms(signedUpAt, marketing);
        NotificationSetting setting = NotificationSetting.createDefault(created.getId());
        setting.changeEmail(alertEmail);
        notificationSettingRepository.saveAndFlush(setting);
        userRepository.saveAndFlush(created);
        jdbcTemplate.update("update users set created_at = ? where id = ?", signedUpAt, created.getId());
        return created;
    }

    private void recordAt(User owner, LocalDateTime recordedAt) {
        Recording recording = recordingRepository.saveAndFlush(Recording.create(owner.getId(), "reminder.m4a", 600));
        jdbcTemplate.update("update recordings set created_at = ? where id = ?", recordedAt, recording.getId());
    }

    private void remindedAt(User owner, LocalDateTime remindedAt, NotificationLogStatus status) {
        NotificationLog log = notificationLogRepository.saveAndFlush(
                NotificationLog.pending(owner.getId(), NotificationType.MARKETING_REMINDER, null));
        jdbcTemplate.update("update notification_logs set created_at = ?, status = ? where id = ?",
                remindedAt, status.name(), log.getId());
    }

    private void run(LocalDateTime now) {
        entityManager.flush();
        entityManager.clear();
        marketingReminderService.sendDueReminders(now);
    }

    private EmailMessage sentTo(String email) {
        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, atLeastOnce()).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(message -> message.to().equals(email))
                .findFirst()
                .orElseThrow(() -> new AssertionError(email + "로 보낸 메일이 없습니다."));
    }

    private void verifyNotSentTo(String email) {
        verify(emailSender, never()).send(argThat(message -> message.to().equals(email)));
    }

    private List<NotificationLog> remindersOf(User owner) {
        return notificationLogRepository.findAll().stream()
                .filter(log -> log.getUserId().equals(owner.getId()))
                .filter(log -> log.getType() == NotificationType.MARKETING_REMINDER)
                .toList();
    }

    @Test
    void 가입_후_녹음하지_않고_주기가_지나면_첫_녹음을_권하는_광고_메일을_보낸다() {
        run(NOW);

        EmailMessage message = sentTo(EMAIL);
        assertThat(message.subject()).isEqualTo("(광고) [RE:SAY] 지현님, 첫 대화를 기록해 볼까요?");
        assertThat(message.headers()).containsKey("List-Unsubscribe")
                .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
        assertThat(remindersOf(user)).singleElement()
                .extracting(NotificationLog::getStatus).isEqualTo(NotificationLogStatus.SENT);
    }

    @Test
    void 녹음한_지_주기가_지났으면_마지막_기록_날짜를_알려준다() {
        recordAt(user, NOW.minusDays(10));

        run(NOW);

        assertThat(sentTo(EMAIL).subject()).isEqualTo("(광고) [RE:SAY] 지현님, 마지막 대화 기록이 10일 전이에요");
    }

    @Test
    void 최근에_녹음했으면_보내지_않는다() {
        recordAt(user, NOW.minusDays(10));
        recordAt(user, NOW.minusDays(3));

        run(NOW);

        verifyNotSentTo(EMAIL);
    }

    @Test
    void 가입한_지_주기가_안_됐으면_보내지_않는다() {
        jdbcTemplate.update("update users set created_at = ? where id = ?", NOW.minusDays(6), user.getId());

        run(NOW);

        verifyNotSentTo(EMAIL);
    }

    @Test
    void 주기_안에_리마인드를_받았으면_보내지_않는다() {
        remindedAt(user, NOW.minusDays(3), NotificationLogStatus.SENT);

        run(NOW);

        verifyNotSentTo(EMAIL);
    }

    @Test
    void 계속_쓰지_않으면_지난_리마인드로부터_주기마다_다시_보낸다() {
        remindedAt(user, NOW.minusDays(7), NotificationLogStatus.SENT);

        run(NOW);

        sentTo(EMAIL);
    }

    @Test
    void 실패한_리마인드는_받은_것으로_치지_않고_다시_보낸다() {
        remindedAt(user, NOW.minusDays(1), NotificationLogStatus.FAILED);

        run(NOW);

        sentTo(EMAIL);
    }

    @Test
    void 마케팅_수신에_동의하지_않았으면_보내지_않는다() {
        createUser("no-marketing@example.com", "no-marketing-alert@example.com", false, NOW.minusDays(30));

        run(NOW);

        verifyNotSentTo("no-marketing-alert@example.com");
    }

    @Test
    void 알림_이메일이_없으면_보내지_않는다() {
        createUser("no-alert@example.com", null, true, NOW.minusDays(30));

        run(NOW);

        verify(emailSender, never()).send(argThat(message -> message.to() == null));
    }

    @Test
    void 밤_9시부터_아침_8시까지는_보내지_않는다() {
        run(NOW.withHour(21).withMinute(0));
        run(NOW.withHour(7).withMinute(59));

        verifyNotSentTo(EMAIL);
    }

    @Test
    void 저녁_8시_59분까지는_보낸다() {
        run(NOW.withHour(20).withMinute(59));

        sentTo(EMAIL);
    }

    @Test
    void 한_명이_실패해도_나머지에게는_보내고_실패를_기록한다() {
        User other = createUser("reminder-other@example.com", "reminder-other-alert@example.com", true,
                NOW.minusDays(30));
        given(emailSender.send(argThat(message -> message != null && message.to().equals(EMAIL))))
                .willThrow(new EmailSendException("HTTP_422 validation_error", null));

        run(NOW);

        sentTo("reminder-other-alert@example.com");
        assertThat(remindersOf(user)).singleElement()
                .extracting(NotificationLog::getStatus).isEqualTo(NotificationLogStatus.FAILED);
        assertThat(remindersOf(other)).singleElement()
                .extracting(NotificationLog::getStatus).isEqualTo(NotificationLogStatus.SENT);
    }
}
