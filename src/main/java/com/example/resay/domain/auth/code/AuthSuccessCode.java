package com.example.resay.domain.auth.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    SIGNUP(HttpStatus.CREATED, "AUTH201_1", "회원가입이 완료되었습니다."),
    LOGIN(HttpStatus.OK, "AUTH200_1", "로그인되었습니다."),
    REISSUE(HttpStatus.OK, "AUTH200_2", "토큰이 재발급되었습니다."),
    LOGOUT(HttpStatus.OK, "AUTH200_3", "로그아웃되었습니다."),
    SOCIAL_LOGIN(HttpStatus.OK, "AUTH200_4", "소셜 로그인되었습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
