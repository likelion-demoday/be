package com.example.resay.domain.credit.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CreditErrorCode implements BaseErrorCode {

    INVALID_ADJUSTMENT(HttpStatus.BAD_REQUEST, "CREDIT400_1", "조정할 수 없는 금액입니다."),
    // 프론트는 이 코드를 받으면 충전 화면으로 보낸다. error에 필요한 크레딧 · 잔액 · 부족분이 들어간다
    INSUFFICIENT_CREDIT(HttpStatus.PAYMENT_REQUIRED, "CREDIT402_1", "크레딧이 부족합니다."),
    // 같은 대상에 대한 요청이 동시에 처리된 경우. 다시 요청하면 처리 결과를 받는다
    USAGE_CONFLICT(HttpStatus.CONFLICT, "CREDIT409_1", "이미 처리 중인 요청입니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
