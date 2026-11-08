package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisCompletedMailTemplateTest {

    private final AnalysisCompletedMailTemplate template =
            new AnalysisCompletedMailTemplate(new NotificationMailProperties("https://resay.site/", "https://api.resay.site"));

    @Test
    void 닉네임과_보고서_링크를_넣는다() {
        EmailMessage message = template.create("user@example.com", "지현", 42L);

        assertThat(message.to()).isEqualTo("user@example.com");
        assertThat(message.subject()).isEqualTo("[RE:SAY] 대화 분석이 완료됐어요");
        assertThat(message.html())
                .contains("지현님, 대화 분석이 완료됐어요")
                .contains("href=\"https://resay.site/report/42\"")
                .contains("href=\"https://resay.site/mypage/notifications\"");
        assertThat(message.text())
                .contains("지현님")
                .contains("https://resay.site/report/42");
        assertThat(message.idempotencyKey()).isEqualTo("analysis-completed-42");
    }

    @Test
    void 닉네임의_HTML은_그대로_실행되지_않게_바꾼다() {
        EmailMessage message = template.create("user@example.com", "<b>해커</b>", 1L);

        assertThat(message.html()).contains("&lt;b&gt;해커&lt;/b&gt;님").doesNotContain("<b>해커</b>");
    }

    @Test
    void 닉네임이_없으면_회원으로_부른다() {
        assertThat(template.create("user@example.com", " ", 1L).text()).startsWith("회원님");
    }
}
