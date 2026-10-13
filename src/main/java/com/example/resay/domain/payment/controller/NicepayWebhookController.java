package com.example.resay.domain.payment.controller;

import com.example.resay.domain.payment.service.PaymentWebhookService;
import io.swagger.v3.oas.annotations.Hidden;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 나이스페이 서버가 결제 · 취소가 일어났을 때 통보(웹훅)를 보내는 주소. 나이스페이 관리자 화면에 이 주소를 등록해야 온다.
 * 브라우저를 거치지 않는 서버 간 호출이고 로그인 없이 열려 있다(SecurityConfig). 내용은 서명과 조회로 확인한다.
 *
 * 응답은 나이스페이가 정한 형식이다: HTTP 200에 본문이 "OK"(text/html)여야 성공으로 기록된다.
 * 그 밖의 응답은 실패로 기록되고, 나이스페이 관리자 화면에서 다시 보낼 수 있다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class NicepayWebhookController {

    private static final MediaType TEXT_HTML_UTF8 = new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8);

    private final PaymentWebhookService paymentWebhookService;

    // 본문을 그대로 받아서 직접 읽는다 (나이스페이가 보내는 Content-Type에 기대지 않는다)
    @PostMapping("/api/v1/payments/nicepay/webhook")
    public ResponseEntity<String> receive(@RequestBody(required = false) byte[] body) {
        boolean handled = paymentWebhookService.handle(body);
        return ResponseEntity.status(handled ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(TEXT_HTML_UTF8)
                .body(handled ? "OK" : "FAIL");
    }
}
