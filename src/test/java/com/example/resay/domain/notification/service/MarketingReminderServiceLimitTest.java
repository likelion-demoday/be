package com.example.resay.domain.notification.service;

import com.example.resay.domain.notification.config.MarketingReminderProperties;
import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.mail.MarketingReminderMailTemplate;
import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.port.EmailSendResult;
import com.example.resay.domain.notification.port.EmailSender;
import com.example.resay.domain.notification.repository.NotificationSettingRepository;
import com.example.resay.domain.notification.repository.ReminderCandidate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// 하루 상한 · 순서 · 끄기 스위치는 설정값에 달려 있어 스프링 없이 직접 만들어 확인한다
class MarketingReminderServiceLimitTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 19, 0);

    private final NotificationSettingRepository repository = mock(NotificationSettingRepository.class);
    private final NotificationLogService logService = mock(NotificationLogService.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final NotificationQuotaService quotaService = mock(NotificationQuotaService.class);

    {
        given(quotaService.remainingForDeferrable(any())).willReturn(80);
    }

    private MarketingReminderService service(boolean enabled, int dailyLimit) {
        MarketingReminderProperties properties =
                new MarketingReminderProperties(enabled, 7, dailyLimit, Duration.ZERO, "contact@example.com");
        MarketingReminderMailTemplate template = new MarketingReminderMailTemplate(
                new NotificationMailProperties("https://resay.site", "https://api.resay.site"),
                properties,
                new UnsubscribeTokenSigner("test-only-secret-0123456789-0123456789"));
        return new MarketingReminderService(repository, logService, quotaService, template, emailSender, properties);
    }

    @Test
    void 같은_날_다른_알림으로_한도를_썼으면_남은_만큼만_보낸다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(
                candidate(1, 30), candidate(2, 20), candidate(3, 10)));
        given(quotaService.remainingForDeferrable(any())).willReturn(1);
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg"));

        assertThat(service(true, 80).sendDueReminders(NOW)).isEqualTo(1);
        verify(emailSender, times(1)).send(any());
    }

    private static ReminderCandidate candidate(long userId, int daysSinceSignup) {
        return new ReminderCandidate(userId, "사용자" + userId, "user" + userId + "@example.com",
                NOW.minusDays(daysSinceSignup), null, null);
    }

    @Test
    void 하루_상한까지만_보내고_오래_쓰지_않은_사용자부터_보낸다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(
                candidate(1, 10), candidate(2, 30), candidate(3, 20)));
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg"));

        int sent = service(true, 2).sendDueReminders(NOW);

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender, times(2)).send(captor.capture());
        assertThat(captor.getAllValues()).extracting(EmailMessage::to)
                .containsExactly("user2@example.com", "user3@example.com");
        assertThat(sent).isEqualTo(2);
    }

    @Test
    void 예상하지_못한_오류가_나도_나머지에게는_보내고_실패로_기록한다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(
                candidate(1, 30), candidate(2, 20)));
        given(logService.start(any(), any())).willReturn(10L, 20L);
        given(emailSender.send(any()))
                .willThrow(new IllegalStateException("요청 변환 실패"))
                .willReturn(EmailSendResult.sent("msg"));

        int sent = service(true, 80).sendDueReminders(NOW);

        assertThat(sent).isEqualTo(1);
        verify(logService).markFailed(10L, "unexpected_error");
        verify(logService).markResult(20L, EmailSendResult.sent("msg"));
    }

    @Test
    void 발송_기록을_만들지_못한_사용자는_보내지_않고_건너뛴다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(
                candidate(1, 30), candidate(2, 20)));
        given(logService.start(any(), any()))
                .willThrow(new IllegalStateException("DB 오류"))
                .willReturn(20L);
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg"));

        int sent = service(true, 80).sendDueReminders(NOW);

        assertThat(sent).isEqualTo(1);
        verify(emailSender, times(1)).send(any());
    }

    @Test
    void 메일을_보낸_뒤_결과_기록에_실패해도_실패로_바꾸지_않는다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(candidate(1, 30)));
        given(logService.start(any(), any())).willReturn(10L);
        given(emailSender.send(any())).willReturn(EmailSendResult.sent("msg"));
        org.mockito.BDDMockito.willThrow(new IllegalStateException("DB 오류"))
                .given(logService).markResult(any(), any());

        assertThat(service(true, 80).sendDueReminders(NOW)).isEqualTo(1);
        verify(logService, never()).markFailed(any(), any());
    }

    @Test
    void 꺼_두면_아무것도_보내지_않는다() {
        given(repository.findReminderCandidates(any(), any())).willReturn(List.of(candidate(1, 30)));

        assertThat(service(false, 80).sendDueReminders(NOW)).isZero();

        verify(emailSender, never()).send(any());
    }
}
