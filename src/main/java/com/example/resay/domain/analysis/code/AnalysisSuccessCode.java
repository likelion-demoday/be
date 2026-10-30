package com.example.resay.domain.analysis.code;

import com.example.resay.global.apiPayload.code.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AnalysisSuccessCode implements BaseSuccessCode {

    REPORT_GET(HttpStatus.OK, "ANALYSIS200_1", "분석 보고서를 조회했습니다."),
    LIST_GET(HttpStatus.OK, "ANALYSIS200_2", "분석 목록을 조회했습니다."),
    DELETE(HttpStatus.OK, "ANALYSIS200_3", "분석 결과를 삭제했습니다."),
    SUMMARY_GET(HttpStatus.OK, "ANALYSIS200_4", "개인화 대화 습관 요약을 조회했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
