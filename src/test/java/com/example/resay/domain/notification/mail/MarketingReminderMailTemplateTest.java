package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.MarketingReminderProperties;
import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.service.UnsubscribeTokenSigner;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class MarketingReminderMailTemplateTest {

    private final UnsubscribeTokenSigner signer = new UnsubscribeTokenSigner("test-only-secret-0123456789-0123456789");
    private final MarketingReminderMailTemplate template = new MarketingReminderMailTemplate(
            new NotificationMailProperties("https://resay.site", "https://api.resay.site"),
            new MarketingReminderProperties(true, 7, 80, Duration.ZERO, "contact@example.com"),
            signer);

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 20);

    @Test
    void 제목_맨_앞에_광고_표시를_한다() {
        assertThat(template.create("a@example.com", "지현", 1L, null, TODAY).subject())
                .isEqualTo("(광고) [RE:SAY] 지현님, 첫 대화를 기록해 볼까요?");
        assertThat(template.create("a@example.com", "지현", 1L, 8L, TODAY).subject())
                .isEqualTo("(광고) [RE:SAY] 지현님, 마지막 대화 기록이 8일 전이에요");
    }

    @Test
    void 전송자_연락처와_로그인_없는_수신_거부_링크를_넣는다() {
        EmailMessage message = template.create("a@example.com", "지현", 1L, null, TODAY);
        String unsubscribeUrl = "https://api.resay.site/api/v1/notifications/unsubscribe?u=1&t=" + signer.sign(1L);

        assertThat(message.html())
                .contains("광고성 메일")
                .contains("보내는 사람: RE:SAY")
                .contains("contact@example.com")
                .contains("href=\"" + unsubscribeUrl.replace("&", "&amp;") + "\"")
                .contains("href=\"https://resay.site/home\"");
        assertThat(message.text())
                .contains(unsubscribeUrl)
                .contains("contact@example.com");
        assertThat(message.headers())
                .containsEntry("List-Unsubscribe", "<" + unsubscribeUrl + ">")
                .containsEntry("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
    }

    @Test
    void 같은_날_같은_사용자에게는_같은_중복_방지_키를_쓴다() {
        assertThat(template.create("a@example.com", "지현", 1L, null, TODAY).idempotencyKey())
                .isEqualTo("marketing-reminder-1-2026-11-20");
    }

    @Test
    void 닉네임의_HTML은_그대로_실행되지_않게_바꾼다() {
        assertThat(template.create("a@example.com", "<b>해커</b>", 1L, null, TODAY).html())
                .contains("&lt;b&gt;해커&lt;/b&gt;님")
                .doesNotContain("<b>해커</b>");
    }
}
