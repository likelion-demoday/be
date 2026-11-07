package com.example.resay.domain.notification.mail;

import com.example.resay.domain.notification.config.MarketingReminderProperties;
import com.example.resay.domain.notification.config.NotificationMailProperties;
import com.example.resay.domain.notification.port.EmailMessage;
import com.example.resay.domain.notification.service.UnsubscribeTokenSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.util.Map;

// 미사용자 마케팅 리마인드 메일 (광고).
// 정보통신망법에 따라 제목 맨 앞에 (광고), 본문에 전송자 이름 · 연락처 · 로그인 없이 누를 수 있는 수신 거부 방법을 넣는다.
// 대화 내용 · 분석 결과 · 대화 상대는 넣지 않는다
@Component
@RequiredArgsConstructor
public class MarketingReminderMailTemplate {

    static final String SENDER_NAME = "RE:SAY";
    private static final String DEFAULT_NICKNAME = "회원";

    private final NotificationMailProperties mailProperties;
    private final MarketingReminderProperties reminderProperties;
    private final UnsubscribeTokenSigner unsubscribeTokenSigner;

    // daysSinceLastRecording이 null이면 가입 후 녹음한 적이 없는 사용자다
    public EmailMessage create(String to, String nickname, Long userId, Long daysSinceLastRecording, LocalDate sentDate) {
        String name = nickname == null || nickname.isBlank() ? DEFAULT_NICKNAME : nickname;
        String headline = daysSinceLastRecording == null
                ? name + "님, 첫 대화를 기록해 볼까요?"
                : name + "님, 마지막 대화 기록이 " + daysSinceLastRecording + "일 전이에요";
        String body = daysSinceLastRecording == null
                ? "가까운 사람과 나눈 대화를 녹음하면 RE:SAY가 대화 습관을 분석해 드려요."
                : "요즘 대화는 어떠세요? 오늘 나눈 대화를 기록하고 분석 결과를 받아 보세요.";
        String homeUrl = mailProperties.frontendBaseUrl() + "/home";
        String unsubscribeUrl = mailProperties.apiBaseUrl() + "/api/v1/notifications/unsubscribe?u=" + userId
                + "&t=" + unsubscribeTokenSigner.sign(userId);

        return new EmailMessage(
                to,
                "(광고) [RE:SAY] " + headline,
                html(HtmlUtils.htmlEscape(headline), body, homeUrl, unsubscribeUrl),
                text(headline, body, homeUrl, unsubscribeUrl),
                // 같은 날 같은 사용자에게는 메일 서비스에서도 한 번만 나가게 한다
                "marketing-reminder-" + userId + "-" + sentDate,
                // 메일 앱의 "구독 취소" 버튼으로 바로 수신 거부한다 (RFC 8058 원클릭)
                Map.of(
                        "List-Unsubscribe", "<" + unsubscribeUrl + ">",
                        "List-Unsubscribe-Post", "List-Unsubscribe=One-Click"
                )
        );
    }

    private String html(String escapedHeadline, String body, String homeUrl, String unsubscribeUrl) {
        String escapedUnsubscribeUrl = HtmlUtils.htmlEscape(unsubscribeUrl);
        String contact = HtmlUtils.htmlEscape(reminderProperties.contactEmail());
        return """
                <!doctype html>
                <html lang="ko">
                <body style="margin:0;padding:0;background:#f5f5f7;">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background:#f5f5f7;padding:32px 16px;">
                    <tr><td align="center">
                      <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:480px;background:#ffffff;border-radius:16px;padding:32px 24px;font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1d1d1f;">
                        <tr><td style="font-size:20px;font-weight:700;padding-bottom:16px;">%s</td></tr>
                        <tr><td style="font-size:15px;line-height:1.6;color:#424245;padding-bottom:24px;">%s</td></tr>
                        <tr><td style="padding-bottom:32px;">
                          <a href="%s" style="display:inline-block;background:#1d1d1f;color:#ffffff;text-decoration:none;font-size:15px;font-weight:600;padding:14px 24px;border-radius:10px;">대화 기록하러 가기</a>
                        </td></tr>
                        <tr><td style="font-size:12px;line-height:1.6;color:#86868b;border-top:1px solid #e5e5ea;padding-top:16px;">
                          이 메일은 마케팅 정보 수신에 동의한 분께 보내드리는 광고성 메일이에요.<br>
                          더 이상 받지 않으려면 <a href="%s" style="color:#86868b;">수신 거부</a>를 눌러 주세요. 로그인하지 않아도 돼요.<br>
                          보내는 사람: %s · 문의: %s
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(escapedHeadline, body, homeUrl, escapedUnsubscribeUrl, SENDER_NAME, contact);
    }

    private String text(String headline, String body, String homeUrl, String unsubscribeUrl) {
        return """
                %s

                %s
                대화 기록하러 가기: %s

                이 메일은 마케팅 정보 수신에 동의한 분께 보내드리는 광고성 메일이에요.
                수신 거부 (로그인 필요 없음): %s
                보내는 사람: %s · 문의: %s
                """.formatted(headline, body, homeUrl, unsubscribeUrl, SENDER_NAME, reminderProperties.contactEmail());
    }
}
