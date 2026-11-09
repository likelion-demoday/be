package com.example.resay.domain.share.code;

import com.example.resay.global.apiPayload.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ShareErrorCode implements BaseErrorCode {

    SHARED_RESOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "SHARE404_1",
            "공유 링크가 없거나 만료되었습니다."
    ),
    ANALYSIS_NOT_SHAREABLE(
            HttpStatus.CONFLICT,
            "SHARE409_1",
            "완료된 분석 보고서만 공유할 수 있습니다."
    );

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
