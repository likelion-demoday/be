package com.example.resay.domain.auth.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "AUTH409_1", "이미 가입된 이메일입니다."),
    // 가입 여부가 드러나지 않도록 이메일 미존재와 비밀번호 불일치를 같은 응답으로 처리한다
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH401_1", "이메일 또는 비밀번호가 일치하지 않습니다."),
    // 프론트는 이 코드를 받으면 로그인 화면으로 보낸다
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH401_2", "로그인이 만료되었습니다. 다시 로그인해 주세요.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
