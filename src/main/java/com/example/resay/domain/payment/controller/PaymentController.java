package com.example.resay.domain.payment.controller;

import com.example.resay.domain.payment.code.PaymentSuccessCode;
import com.example.resay.domain.payment.dto.PaymentCreateRequestDto;
import com.example.resay.domain.payment.dto.PaymentHistoryResponseDto;
import com.example.resay.domain.payment.dto.PaymentOrderResponseDto;
import com.example.resay.domain.payment.dto.PaymentProductResponseDto;
import com.example.resay.domain.payment.dto.PaymentResponseDto;
import com.example.resay.domain.payment.service.PaymentOrderService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentOrderService paymentOrderService;

    @Operation(summary = "충전 상품 조회", description = "충전할 수 있는 금액 목록. 금액과 지급 크레딧은 서버 설정으로 정한다.")
    @GetMapping("/products")
    public ApiResponse<PaymentProductResponseDto> getProducts() {
        return ApiResponse.onSuccess(PaymentSuccessCode.GET_PRODUCTS, paymentOrderService.getProducts());
    }

    // 충전 주문을 만든다. 응답 값을 그대로 결제창(AUTHNICE.requestPay)에 넘긴다.
    // 결제가 끝나면 이 요청을 보낸 프론트 주소(Origin)의 /payments/success 또는 /payments/fail 로 돌아온다
    @Operation(summary = "충전 주문 생성", description = """
            결제 버튼을 누를 때 호출한다 (화면에 들어올 때 미리 만들지 않는다). 결제 시도마다 새 주문을 만들고, 주문번호는 다시 쓰지 않는다.

            응답 값을 그대로 나이스페이 결제창에 넘긴다: `AUTHNICE.requestPay({clientId, method, orderId, amount, goodsName, returnUrl, fnError})`.
            스크립트는 `https://pay.nicepay.co.kr/v1/js/` (테스트 · 운영 공통). `fnError`는 필수이고, 사용자가 결제창을 닫으면 이 콜백만 불린다(서버로는 아무것도 오지 않는다).

            결제가 끝나면 브라우저가 이 요청을 보낸 프론트 주소의 `/payments/success?orderId=...` 또는 `/payments/fail?orderId=...` 로 이동한다.
            모바일에서는 결제 중 페이지 전체가 결제창으로 넘어갔다 돌아오므로, 결과 화면은 주소의 `orderId`만으로 동작해야 한다.

            Swagger처럼 프론트 주소가 아닌 곳에서 호출하면 `PAYMENT400_2`로 거절된다. 짧은 시간에 너무 많이 만들면 `429 COMMON429_1`.
            """)
    @PostMapping
    public ApiResponse<PaymentOrderResponseDto> createOrder(
            @CurrentUserId Long userId,
            // 브라우저가 자동으로 붙이는 헤더라 프론트가 따로 넣을 값이 아니다
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.ORIGIN, required = false) String origin,
            @Valid @RequestBody PaymentCreateRequestDto request
    ) {
        return ApiResponse.onSuccess(
                PaymentSuccessCode.CREATE_ORDER,
                paymentOrderService.create(userId, request.productCode(), origin));
    }

    // 최근 충전부터 내려준다
    @Operation(summary = "충전 내역 조회", description = "결제된 충전만 최근 순으로 내려준다.")
    @GetMapping
    public ApiResponse<PaymentHistoryResponseDto> getMyHistory(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                PaymentSuccessCode.GET_HISTORY, paymentOrderService.getHistory(userId, page, size));
    }

    // 결제 결과 화면이 호출한다. 주소로 넘어온 값은 믿지 않고 여기서 주문 상태를 확인한다
    @Operation(summary = "충전 결과 조회", description = """
            결제 결과 화면(`/payments/success`, `/payments/fail`)에서 호출한다. 어느 화면으로 왔는지가 아니라 이 응답의 `status`로 결과를 표시한다.

            - `PAID`: 결제 완료, 크레딧 지급됨
            - `APPROVING`: 결제 결과 확인 중. 2~3초 간격으로 다시 조회한다. 대부분 바로 끝나지만 드물게 몇 분 걸린다 (성공 화면으로 왔어도 이 상태일 수 있고, 이후 `FAILED`가 될 수 있다)
            - `FAILED`: 결제되지 않음. `failMessage`가 있으면 텍스트로 보여 준다 (없을 수 있다)
            - `READY` · `EXPIRED`: 결제하지 않은 주문. 다시 결제하려면 주문을 새로 만든다
            """)
    @GetMapping("/{orderId}")
    public ApiResponse<PaymentResponseDto> getPayment(
            @CurrentUserId Long userId,
            @PathVariable String orderId
    ) {
        return ApiResponse.onSuccess(PaymentSuccessCode.GET_PAYMENT, paymentOrderService.get(userId, orderId));
    }
}
