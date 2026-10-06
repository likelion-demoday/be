package com.example.resay.domain.recording.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordingSuccessCode implements BaseSuccessCode {

    UPLOAD(HttpStatus.ACCEPTED, "RECORDING202_1", "녹음 파일을 업로드했습니다."),
    SELECT_TYPE(HttpStatus.OK, "RECORDING200_1", "관계유형을 선택했습니다."),
    MAP_SPEAKERS(HttpStatus.OK, "RECORDING200_2", "화자를 지정했습니다. 분석을 시작합니다."),
    GET_STATUS(HttpStatus.OK, "RECORDING200_3", "녹음 상태를 조회했습니다."),
    GET_SPEAKER_SAMPLES(HttpStatus.OK, "RECORDING200_4", "화자 샘플을 조회했습니다."),
    DELETE(HttpStatus.OK, "RECORDING200_5", "대화를 삭제했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
