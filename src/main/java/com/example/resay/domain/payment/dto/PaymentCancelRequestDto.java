package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.entity.PaymentCancel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentCancelRequestDto(
        // 기록으로만 남는다. 사용자에게도 나이스페이에도 보내지 않는다
        @NotBlank(message = "환불 사유를 입력해 주세요.")
        @Size(max = PaymentCancel.REASON_MAX_LENGTH, message = "환불 사유는 200자 이하여야 합니다.")
        String reason
) {

    public PaymentCancelRequestDto {
        reason = reason == null ? null : reason.trim();
    }
}
