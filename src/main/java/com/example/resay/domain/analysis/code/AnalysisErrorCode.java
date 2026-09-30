package com.example.resay.domain.analysis.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AnalysisErrorCode implements BaseErrorCode {

    ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "ANALYSIS404_1", "분석 작업을 찾을 수 없습니다."),
    ANALYSIS_ALREADY_EXISTS(HttpStatus.CONFLICT, "ANALYSIS409_1", "이미 생성된 분석 작업입니다."),
    INVALID_ANALYSIS_STATUS(HttpStatus.CONFLICT, "ANALYSIS409_2", "현재 상태에서는 분석 상태를 변경할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
