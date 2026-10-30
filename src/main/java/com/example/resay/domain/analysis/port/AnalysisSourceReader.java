package com.example.resay.domain.analysis.port;

import com.example.resay.domain.analysis.model.AnalysisSource;

// B가 저장한 전사 데이터를 C 입력 모델로 읽어오는 경계
@FunctionalInterface
public interface AnalysisSourceReader {

    AnalysisSource read(Long recordingId);
}
