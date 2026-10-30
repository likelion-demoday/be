package com.example.resay.domain.payment.controller;

import com.example.resay.domain.payment.code.PaymentSuccessCode;
import com.example.resay.domain.payment.dto.AdminPaymentSearchRequestDto;
import com.example.resay.domain.payment.dto.AdminPaymentSearchResponseDto;
import com.example.resay.domain.payment.dto.PaymentCancelRequestDto;
import com.example.resay.domain.payment.dto.PaymentCancelResponseDto;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.service.PaymentCancelService;
import com.example.resay.domain.payment.service.PaymentOrderService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/v1/admin/** 은 ADMIN 권한이 있어야 접근할 수 있다 (SecurityConfig)
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentCancelService paymentCancelService;
    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "충전 주문 조회 (환불할 주문 찾기)", description = """
            사용자의 이메일로 결제된 충전(환불 중 · 환불된 것 포함, 최근 50건)과 지금 잔액을 조회한다.
            환불 API에 넣을 주문번호(`orderId`)를 여기서 찾는다.

            `refundable`이 `true`인 주문만 전액 환불할 수 있다 (결제 완료 상태이고 잔액이 그 충전의 크레딧 이상).
            한 건을 환불하면 잔액이 줄어 다른 주문의 `refundable`이 바뀔 수 있으니 환불할 때마다 다시 조회한다.

            - `404 USER404_1`: 그 이메일로 가입한 사용자가 없다
            """)
    @PostMapping("/search")
    public ApiResponse<AdminPaymentSearchResponseDto> search(@Valid @RequestBody AdminPaymentSearchRequestDto request) {
        return ApiResponse.onSuccess(
                PaymentSuccessCode.SEARCH_PAYMENTS, paymentOrderService.searchByEmail(request.email()));
    }

    @Operation(summary = "충전 환불 (전액 취소)", description = """
            충전 주문의 결제를 전액 취소하고 지급했던 크레딧을 회수한다. 충전한 크레딧이 그대로 남아 있는 주문만 된다.

            - `200 PAYMENT200_5`: 환불 완료 (`status`가 `CANCELLED`)
            - `202 PAYMENT202_1`: 나이스페이의 답을 받지 못해 취소 여부를 확인하는 중 (`status`가 `CANCELLING`). 크레딧은 회수된 상태이고 몇 분 안에 `CANCELLED` 또는 `PAID`로 확정된다. 다시 요청하지 않는다
            - `409 PAYMENT409_1`: 결제 완료 상태가 아닌 주문 (결제 전 · 실패 · 이미 환불됨 · 환불 확인 중). `error.status`에 지금 상태가 있다
            - `409 PAYMENT409_2`: 나이스페이가 취소를 거절했다. 회수한 크레딧은 되돌렸고 `error.failCode` · `error.failMessage`에 이유가 있다
            - `409 CREDIT409_2`: 충전한 크레딧을 일부라도 써서 잔액이 충전 금액보다 적다. 이 경우는 수동으로 처리한다 (계좌이체 + 크레딧 조정)
            """)
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<PaymentCancelResponseDto>> cancel(
            @CurrentUserId Long adminId,
            @PathVariable String orderId,
            @Valid @RequestBody PaymentCancelRequestDto request
    ) {
        PaymentCancelResponseDto result = paymentCancelService.cancel(orderId, request.reason(), adminId);
        PaymentSuccessCode code = result.status() == PaymentStatus.CANCELLED
                ? PaymentSuccessCode.CANCEL_DONE
                : PaymentSuccessCode.CANCEL_PENDING;
        return ResponseEntity.status(code.getHttpStatus()).body(ApiResponse.onSuccess(code, result));
    }
}
