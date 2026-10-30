package com.example.resay.domain.character.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum CharacterImageErrorCode implements BaseErrorCode {

    CHARACTER_IMAGE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "CHARACTER404_1",
            "캐릭터 이미지를 찾을 수 없습니다."
    ),
    INVALID_ANALYSIS_STATUS(
            HttpStatus.CONFLICT,
            "CHARACTER409_1",
            "분석이 완료된 대화만 캐릭터 이미지를 생성할 수 있습니다."
    ),
    CHARACTER_IMAGE_NOT_READY(
            HttpStatus.CONFLICT,
            "CHARACTER409_2",
            "캐릭터 이미지 생성이 아직 완료되지 않았습니다."
    ),
    CHARACTER_INSIGHT_NOT_FOUND(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "CHARACTER422_1",
            "캐릭터 이미지 생성에 필요한 분석 결과가 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
