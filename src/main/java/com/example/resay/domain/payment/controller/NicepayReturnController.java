package com.example.resay.domain.payment.controller;

import com.example.resay.domain.payment.dto.NicepayReturnRequestDto;
import com.example.resay.domain.payment.service.PaymentApprovalService;
import io.swagger.v3.oas.annotations.Hidden;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 나이스페이 결제창이 카드 인증을 마친 뒤 사용자의 브라우저를 통해 인증 결과를 보내는 주소 (form POST).
 * 프론트 코드가 직접 부르는 API가 아니다. PC에서는 결제창 스크립트가 프론트 페이지에서 form을 제출하고,
 * 모바일에서는 나이스페이 쪽 페이지가 제출한다. 그래서 로그인 없이 열려 있고(SecurityConfig) 출처 검사도 하지 않는다(CorsConfig).
 * 대신 주문 · 금액 · 서명을 확인한 뒤에만 승인한다.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class NicepayReturnController {

    private final PaymentApprovalService paymentApprovalService;

    @PostMapping("/api/v1/payments/nicepay/return")
    public ResponseEntity<Void> receiveAuthResult(@ModelAttribute NicepayReturnRequestDto authResult) {
        URI resultPage = paymentApprovalService.handleAuthResult(authResult);
        // 303: 브라우저가 프론트 결과 화면을 GET으로 연다 (POST를 그대로 다시 보내는 307 · 308이 아니다)
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(resultPage).build();
    }
}
