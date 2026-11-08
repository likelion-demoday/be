package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;

// 관리자 서비스 알림 메일 (점검 · 약관 변경 · 공지). 광고가 아니라 (광고) 표시 · 야간 제한은 두지 않는다.
// 관리자가 쓴 본문은 일반 텍스트로 다룬다. HTML은 그대로 실행되지 않게 바꾸고 줄바꿈만 살린다
@Component
@RequiredArgsConstructor
public class ServiceAnnouncementMailTemplate {

    static final String SUBJECT_PREFIX = "[RE:SAY] ";

    private final NotificationMailProperties properties;

    public EmailMessage create(String to, Long userId, String subject, String body) {
        String settingsUrl = properties.frontendBaseUrl() + "/mypage/notifications";
        return new EmailMessage(
                to,
                SUBJECT_PREFIX + subject,
                html(HtmlUtils.htmlEscape(subject), htmlBody(body), settingsUrl),
                text(subject, body, settingsUrl),
                // 관리자가 같은 공지를 실수로 두 번 보내도 메일 서비스가 24시간 안에는 한 번만 보낸다
                "service-" + contentHash(subject, body) + "-" + userId,
                Map.of()
        );
    }

    private static String htmlBody(String body) {
        return body.lines().map(HtmlUtils::htmlEscape).collect(Collectors.joining("<br>"));
    }

    private static String html(String escapedSubject, String escapedBody, String settingsUrl) {
        return """
                <!doctype html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#f5f5f7;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f7;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1d1d1f;">
                        <tr><td style="font-size:20px;font-weight:700;padding-bottom:16px;">%s</td></tr>
                        <tr><td style="font-size:15px;line-height:1.6;color:#424245;padding-bottom:32px;">%s</td></tr>
                        <tr><td style="font-size:12px;line-height:1.6;color:#86868b;border-top:1px solid #e5e5ea;padding-top:16px;">이 메일은 서비스 알림을 켠 분께 보내드려요.<br>알림을 그만 받으려면 <a href="%s" style="color:#86868b;">알림 설정</a>에서 끌 수 있어요.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapedSubject, escapedBody, settingsUrl);
    }

    private static String text(String subject, String body, String settingsUrl) {
        return """
                %s

                %s

                이 메일은 서비스 알림을 켠 분께 보내드려요.
                알림 설정: %s
                """.formatted(subject, body, settingsUrl);
    }

    private static String contentHash(String subject, String body) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((subject + "\n" + body).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
