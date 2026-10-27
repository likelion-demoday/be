package com.example.resay.domain.analysis.service;

/**
 * 운영 로그와 모니터링에서 사용하는 상세 실패 분류다.
 * 외부 응답에는 이 값을 노출하지 않고 {@code AnalysisFailureReason}만 사용한다.
 */
public enum AnalysisFailureCategory {
    INSUFFICIENT_INPUT,
    PROVIDER_CONFIGURATION,
    PROVIDER_AUTHENTICATION,
    PROVIDER_RATE_LIMIT,
    PROVIDER_TIMEOUT,
    PROVIDER_NETWORK,
    PROVIDER_SERVER_ERROR,
    RESPONSE_LENGTH_EXCEEDED,
    RESPONSE_INCOMPLETE,
    RESPONSE_FORMAT_ERROR,
    RESPONSE_VALIDATION_ERROR,
    PERSISTENCE_ERROR,
    UNEXPECTED_ERROR
}
