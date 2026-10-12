package com.example.resay.domain.payment.dto;

import jakarta.validation.constraints.NotBlank;

// 금액은 받지 않는다. 상품 코드만 받고 금액은 서버가 정한다
public record PaymentCreateRequestDto(
        @NotBlank(message = "상품 코드를 입력해 주세요.")
        String productCode
) {
}
