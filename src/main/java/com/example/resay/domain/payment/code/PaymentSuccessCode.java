package com.example.resay.domain.payment.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PaymentSuccessCode implements BaseSuccessCode {

    GET_PRODUCTS(HttpStatus.OK, "PAYMENT200_1", "충전 상품을 조회했습니다."),
    CREATE_ORDER(HttpStatus.OK, "PAYMENT200_2", "충전 주문을 만들었습니다."),
    GET_PAYMENT(HttpStatus.OK, "PAYMENT200_3", "충전 결과를 조회했습니다."),
    GET_HISTORY(HttpStatus.OK, "PAYMENT200_4", "충전 내역을 조회했습니다."),
    CANCEL_DONE(HttpStatus.OK, "PAYMENT200_5", "충전을 환불했습니다."),
    // 나이스페이의 답을 받지 못했다. 크레딧은 회수된 상태이고, 정리 작업이 취소 여부를 확인해 확정한다
    CANCEL_PENDING(HttpStatus.ACCEPTED, "PAYMENT202_1", "환불을 요청했습니다. 취소 결과를 확인하는 중입니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
