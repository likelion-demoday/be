package com.example.resay.domain.analysis.port;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;

// C 입력 모델을 AI에 보내고 결과를 받는 경계
@FunctionalInterface
public interface AnalysisModelClient {

    AnalysisModelResult analyze(AnalysisSource source);
}
