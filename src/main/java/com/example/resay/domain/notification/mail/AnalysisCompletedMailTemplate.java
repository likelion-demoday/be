package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

// 분석 완료 알림 메일.
// 알림 이메일은 인증하지 않고 받으므로 잘못 입력한 주소로 갈 수 있다. 그래서 대화 내용 · 분석 결과 · 대화 상대는 넣지 않고 링크만 준다
@Component
@RequiredArgsConstructor
public class AnalysisCompletedMailTemplate {

    static final String SUBJECT = "[RE:SAY] 대화 분석이 완료됐어요";
    private static final String DEFAULT_NICKNAME = "회원";

    private final NotificationMailProperties properties;

    public EmailMessage create(String to, String nickname, Long recordingId) {
        String name = nickname == null || nickname.isBlank() ? DEFAULT_NICKNAME : nickname;
        String reportUrl = properties.frontendBaseUrl() + "/report/" + recordingId;
        String settingsUrl = properties.frontendBaseUrl() + "/mypage/notifications";

        return new EmailMessage(
                to,
                SUBJECT,
                html(HtmlUtils.htmlEscape(name), reportUrl, settingsUrl),
                text(name, reportUrl, settingsUrl),
                "analysis-completed-" + recordingId,
                Map.of()
        );
    }

    private static String html(String escapedName, String reportUrl, String settingsUrl) {
        return """
                <!doctype html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#f5f5f7;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f7;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1d1d1f;">
                        <tr><td style="font-size:20px;font-weight:700;padding-bottom:16px;">%s님, 대화 분석이 완료됐어요</td></tr>
                        <tr><td style="font-size:15px;line-height:1.6;color:#424245;padding-bottom:24px;">요청하신 대화의 분석 보고서가 준비됐어요.<br>아래 버튼을 눌러 결과를 확인해 보세요.</td></tr>
                        <tr><td style="padding-bottom:32px;">
                          <a href="%s" style="display:inline-block;background:#1d1d1f;color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;padding:14px 24px;border-radius:10px;">결과 보러 가기</a>
                        </td></tr>
                        <tr><td style="font-size:12px;line-height:1.6;color:#86868b;border-top:1px solid #e5e5ea;padding-top:16px;">이 메일은 분석 완료 알림을 켠 분께 보내드려요.<br>알림을 그만 받으려면 <a href="%s" style="color:#86868b;">알림 설정</a>에서 끌 수 있어요.</td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapedName, reportUrl, settingsUrl);
    }

    private static String text(String name, String reportUrl, String settingsUrl) {
        return """
                %s님, 대화 분석이 완료됐어요

                요청하신 대화의 분석 보고서가 준비됐어요.
                결과 보러 가기: %s

                이 메일은 분석 완료 알림을 켠 분께 보내드려요.
                알림 설정: %s
                """.formatted(name, reportUrl, settingsUrl);
    }
}
