package com.example.resay.domain.character.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CharacterImageSuccessCode implements BaseSuccessCode {

    LIST_GET(HttpStatus.OK, "CHARACTER200_1", "캐릭터 이미지 상태를 조회했습니다."),
    REQUESTED(HttpStatus.ACCEPTED, "CHARACTER202_1", "캐릭터 이미지 생성을 요청했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
