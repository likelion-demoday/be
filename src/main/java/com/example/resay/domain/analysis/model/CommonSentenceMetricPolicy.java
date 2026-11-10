package com.example.resay.domain.analysis.model;

import java.util.List;

public final class CommonSentenceMetricPolicy {

    private static final List<QualitativeAnalysis.ReportMetricType> REQUIRED_TYPES = List.of(
            QualitativeAnalysis.ReportMetricType.SENTENCE_COMPLETENESS,
            QualitativeAnalysis.ReportMetricType.ELABORATION,
            QualitativeAnalysis.ReportMetricType.CLARITY,
            QualitativeAnalysis.ReportMetricType.SENTENCE_VARIETY
    );

    private CommonSentenceMetricPolicy() {
    }

    public static List<QualitativeAnalysis.ReportMetricType> requiredTypes() {
        return REQUIRED_TYPES;
    }

    public static boolean isCommon(QualitativeAnalysis.ReportMetricType type) {
        return REQUIRED_TYPES.contains(type);
    }

    public static String title(QualitativeAnalysis.ReportMetricType type) {
        return switch (type) {
            case SENTENCE_COMPLETENESS -> "완결성";
            case ELABORATION -> "확장성";
            case CLARITY -> "명료성";
            case SENTENCE_VARIETY -> "다양성";
            default -> throw new IllegalArgumentException("공통 문장 구사력 지표가 아닙니다: " + type);
        };
    }

    public static String missingDescription(QualitativeAnalysis.ReportMetricType type) {
        return switch (type) {
            case SENTENCE_COMPLETENESS -> "이번 대화에서는 문장의 완결성을 확인할 근거가 부족했어요.";
            case ELABORATION -> "이번 대화에서는 설명의 확장성을 확인할 근거가 부족했어요.";
            case CLARITY -> "이번 대화에서는 표현의 명료성을 확인할 근거가 부족했어요.";
            case SENTENCE_VARIETY -> "이번 대화에서는 문장 표현의 다양성을 확인할 근거가 부족했어요.";
            default -> throw new IllegalArgumentException("공통 문장 구사력 지표가 아닙니다: " + type);
        };
    }
}
