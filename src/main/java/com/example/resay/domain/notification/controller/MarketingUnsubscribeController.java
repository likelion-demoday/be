package com.example.resay.domain.notification.controller;

import com.example.resay.domain.notification.service.MarketingUnsubscribeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.util.HtmlUtils;

// 광고 메일의 수신 거부 링크. 로그인 없이 메일의 서명값(t)으로 본인인지 확인한다.
// - GET: 메일 본문의 링크를 누르면 확인 화면을 보여준다. 메일 보안 검사기가 링크를 미리 열어도 거부되지 않게 GET에서는 바꾸지 않는다
// - POST: 확인 화면의 버튼, 또는 메일 앱의 "구독 취소" 버튼(List-Unsubscribe-Post 원클릭)이 보낸다
@Controller
@RequestMapping("/api/v1/notifications/unsubscribe")
@RequiredArgsConstructor
public class MarketingUnsubscribeController {

    private static final MediaType HTML = new MediaType(MediaType.TEXT_HTML, java.nio.charset.StandardCharsets.UTF_8);

    private final MarketingUnsubscribeService marketingUnsubscribeService;

    // 메일 앱이 링크를 자르거나 바꿔도 JSON 오류 대신 안내 화면을 보여주도록 u는 문자열로 받아 직접 바꾼다
    @GetMapping
    public ResponseEntity<String> confirm(
            @RequestParam(name = "u", required = false) String rawUserId,
            @RequestParam(name = "t", required = false) String token
    ) {
        Long userId = parseUserId(rawUserId);
        if (!marketingUnsubscribeService.isValid(userId, token)) {
            return invalid();
        }
        String action = "?u=" + userId + "&t=" + HtmlUtils.htmlEscape(token);
        return page(HttpStatus.OK, "광고성 정보 수신 거부", """
                <p>RE:SAY의 광고성 메일(마케팅 알림)을 더 이상 받지 않으시겠어요?</p>
                <form method="post" action="%s">
                  <button type="submit" style="background:#1d1d1f;color:#fff;border:0;border-radius:10px;padding:14px 24px;font-size:15px;font-weight:600;cursor:pointer;">수신 거부하기</button>
                </form>
                """.formatted(action));
    }

    @PostMapping
    public ResponseEntity<String> unsubscribe(
            @RequestParam(name = "u", required = false) String rawUserId,
            @RequestParam(name = "t", required = false) String token
    ) {
        Long userId = parseUserId(rawUserId);
        if (!marketingUnsubscribeService.unsubscribe(userId, token)) {
            return invalid();
        }
        return page(HttpStatus.OK, "수신 거부가 완료됐어요", """
                <p>이제 RE:SAY의 광고성 메일을 보내지 않아요.</p>
                <p>분석 완료 · 서비스 알림은 그대로 받으며, 마이페이지 알림 설정에서 언제든 다시 바꿀 수 있어요.</p>
                """);
    }

    private static Long parseUserId(String rawUserId) {
        try {
            return rawUserId == null ? null : Long.valueOf(rawUserId.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ResponseEntity<String> invalid() {
        return page(HttpStatus.BAD_REQUEST, "링크가 올바르지 않아요", """
                <p>수신 거부 링크가 잘못됐거나 일부가 잘렸어요.</p>
                <p>마이페이지 알림 설정에서 마케팅 알림을 끌 수 있어요.</p>
                """);
    }

    private static ResponseEntity<String> page(HttpStatus status, String title, String content) {
        String html = """
                <!doctype html>
                <html lang="ko">
                <head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>%s</title></head>
                <body style="margin:0;padding:48px 16px;background:#f5f5f7;font-family:-apple-system,BlinkMacSystemFont,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;color:#1d1d1f;">
                  <main style="max-width:480px;margin:0 auto;background:#fff;border-radius:16px;padding:32px 24px;line-height:1.6;">
                    <h1 style="font-size:20px;margin:0 0 16px;">%s</h1>
                    %s
                  </main>
                </body>
                </html>
                """.formatted(title, title, content);
        return ResponseEntity.status(status).contentType(HTML).body(html);
    }
}
