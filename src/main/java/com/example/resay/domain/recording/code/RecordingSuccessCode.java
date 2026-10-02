package com.example.resay.domain.recording.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordingSuccessCode implements BaseSuccessCode {

    UPLOAD(HttpStatus.ACCEPTED, "RECORDING202_1", "녹음 파일을 업로드했습니다."),
    SELECT_TYPE(HttpStatus.OK, "RECORDING200_1", "관계유형을 선택했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
