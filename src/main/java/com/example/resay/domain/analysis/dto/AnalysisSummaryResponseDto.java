package com.example.resay.domain.analysis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "최근 분석을 기준으로 집계한 개인화 대화 습관 요약")
public record AnalysisSummaryResponseDto(
        @Schema(description = "요약에 반영된 분석 개수", example = "5")
        int analysisCount,
        List<FrequentExpression> frequentExpressions,
        SwearWordUsage swearWordUsage,
        SpeakingSpeed speakingSpeed,
        List<SpeakingRatioHistory> speakingRatioHistory
) {

    public AnalysisSummaryResponseDto {
        frequentExpressions = List.copyOf(frequentExpressions);
        speakingRatioHistory = List.copyOf(speakingRatioHistory);
    }

    public record FrequentExpression(
            String expression,
            long count
    ) {
    }

    public record SwearWordUsage(
            @Schema(description = "최근 분석에서 확인된 비속어 총 등장 횟수", example = "4")
            long count,
            @Schema(description = "본인 전체 어절 중 비속어 등장 비율", example = "0.2")
            BigDecimal ratePercent
    ) {
    }

    public record SpeakingSpeed(
            @Schema(description = "최근 분석의 본인 발화를 합산한 초당 음절 수", example = "6.48")
            BigDecimal syllablesPerSecond,
            @Schema(description = "한국어 2인 대화 연구 기준 초당 음절 수", example = "5.79")
            BigDecimal referenceSyllablesPerSecond,
            @Schema(description = "연구 기준 대비 차이. 양수는 빠름, 음수는 느림", example = "11.92")
            BigDecimal differencePercent,
            SpeakingSpeedLevel level,
            String description
    ) {
    }

    public enum SpeakingSpeedLevel {
        RELAXED,
        TYPICAL,
        FAST
    }

    public record SpeakingRatioHistory(
            Long recordingId,
            LocalDateTime analyzedAt,
            BigDecimal speakingRatioPercent
    ) {
    }
}
