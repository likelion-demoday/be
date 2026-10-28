package com.example.resay.domain.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

// 가입할 때 받는 약관 동의. 필수 3개는 모두 true여야 가입된다
public record AgreementsDto(
        @NotNull(message = "서비스 이용약관에 동의해 주세요.")
        @AssertTrue(message = "서비스 이용약관에 동의해 주세요.")
        Boolean terms,

        @NotNull(message = "개인정보 수집 · 이용에 동의해 주세요.")
        @AssertTrue(message = "개인정보 수집 · 이용에 동의해 주세요.")
        Boolean privacy,

        @NotNull(message = "만 14세 이상인지 확인해 주세요.")
        @AssertTrue(message = "만 14세 이상인지 확인해 주세요.")
        Boolean ageOver14,

        // 선택. 보내지 않으면 동의하지 않은 것으로 본다
        Boolean marketing
) {

    public boolean marketingAgreed() {
        return Boolean.TRUE.equals(marketing);
    }
}
