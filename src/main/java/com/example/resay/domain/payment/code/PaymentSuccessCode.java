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
    GET_HISTORY(HttpStatus.OK, "PAYMENT200_4", "충전 내역을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
