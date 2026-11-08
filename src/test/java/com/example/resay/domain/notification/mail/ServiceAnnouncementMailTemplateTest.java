package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceAnnouncementMailTemplateTest {

    private final ServiceAnnouncementMailTemplate template = new ServiceAnnouncementMailTemplate(
            new NotificationMailProperties("https://resay.site", "https://api.resay.site"));

    @Test
    void 제목의_HTML도_그대로_실행되지_않게_바꾼다() {
        EmailMessage message = template.create("a@example.com", 1L, "<script>공지</script>", "본문");

        assertThat(message.html()).contains("&lt;script&gt;공지&lt;/script&gt;").doesNotContain("<script>");
        assertThat(message.html()).contains("href=\"https://resay.site/mypage/notifications\"");
    }

    @Test
    void 같은_공지는_같은_중복_방지_키를_쓰고_내용이_다르면_다른_키를_쓴다() {
        String first = template.create("a@example.com", 1L, "점검", "본문").idempotencyKey();
        String same = template.create("a@example.com", 1L, "점검", "본문").idempotencyKey();
        String changed = template.create("a@example.com", 1L, "점검", "본문 수정").idempotencyKey();
        String otherUser = template.create("b@example.com", 2L, "점검", "본문").idempotencyKey();

        assertThat(first).isEqualTo(same).endsWith("-1");
        assertThat(changed).isNotEqualTo(first);
        assertThat(otherUser).isNotEqualTo(first);
    }
}
