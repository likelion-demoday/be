package com.example.resay.domain.credit.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CreditSuccessCode implements BaseSuccessCode {

    GET_SUMMARY(HttpStatus.OK, "CREDIT200_1", "크레딧 잔액을 조회했습니다."),
    GET_HISTORY(HttpStatus.OK, "CREDIT200_2", "크레딧 내역을 조회했습니다."),
    GET_PRICES(HttpStatus.OK, "CREDIT200_3", "크레딧 가격을 조회했습니다."),
    ADJUST(HttpStatus.OK, "CREDIT200_4", "크레딧을 조정했습니다."),
    PAY_ANALYSIS(HttpStatus.OK, "CREDIT200_5", "분석을 결제했습니다. 분석을 시작합니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
