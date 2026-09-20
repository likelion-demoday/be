package com.example.resay.domain.user.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserSuccessCode implements BaseSuccessCode {

    GET_ME(HttpStatus.OK, "USER200_1", "내 정보를 조회했습니다."),
    UPDATE_ME(HttpStatus.OK, "USER200_2", "내 정보를 수정했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
