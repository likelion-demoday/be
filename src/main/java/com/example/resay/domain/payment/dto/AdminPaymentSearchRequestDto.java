package com.example.resay.domain.payment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 이메일은 주소(쿼리 문자열)가 아니라 본문으로 받는다. 주소에 실으면 접속 로그에 개인정보가 남는다
public record AdminPaymentSearchRequestDto(
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {

    public AdminPaymentSearchRequestDto {
        email = email == null ? null : email.trim();
    }
}
