package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisSummaryResponseDto;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.AnalysisSummarySourceProjection;
import com.example.resay.domain.recording.entity.ParentChildRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisSummaryQueryService {

    private static final int RECENT_ANALYSIS_LIMIT = 5;
    private static final int FREQUENT_EXPRESSION_LIMIT = 5;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1_000);
    private static final BigDecimal REFERENCE_SYLLABLES_PER_SECOND = new BigDecimal("5.79");
    private static final BigDecimal RELAXED_UPPER_BOUND = new BigDecimal("5.00");
    private static final BigDecimal TYPICAL_UPPER_BOUND = new BigDecimal("6.50");

    private final AnalysisResultRepository analysisResultRepository;
    private final ObjectMapper objectMapper;

    public AnalysisSummaryResponseDto getSummary(Long userId) {
        List<AnalysisSummarySourceProjection> sources = analysisResultRepository
                .findRecentSummarySources(
                        userId,
                        AnalysisStatus.COMPLETED,
                        PageRequest.of(0, RECENT_ANALYSIS_LIMIT)
                );

        Map<String, Long> expressionCounts = new HashMap<>();
        List<AnalysisSummaryResponseDto.SpeakingRatioHistory> history = new ArrayList<>();
        long swearWordCount = 0;
        long transcribedWordCount = 0;
        long transcribedSyllableCount = 0;
        long speakingDurationMs = 0;

        for (AnalysisSummarySourceProjection source : sources) {
            AnalysisReport report = readReport(source.getResultJson());
            SpeakerRole selfRole = selfRole(
                    report.recordingInfo().scenario(),
                    source.getParentChildRole()
            );
            SpeakerMetrics metrics = selfMetrics(report, selfRole);
            transcribedWordCount += metrics.transcribedWordCount();
            transcribedSyllableCount += metrics.transcribedSyllableCount();
            speakingDurationMs += metrics.speakingDurationMs();
            swearWordCount += collectSwearWordCount(report, selfRole);
            collectFrequentExpressions(report, selfRole, expressionCounts);
            history.add(new AnalysisSummaryResponseDto.SpeakingRatioHistory(
                    source.getRecordingId(),
                    source.getAnalyzedAt(),
                    metrics.speakingRatioPercent()
            ));
        }

        history.sort(Comparator.comparing(
                AnalysisSummaryResponseDto.SpeakingRatioHistory::analyzedAt
        ));
        return new AnalysisSummaryResponseDto(
                sources.size(),
                frequentExpressions(expressionCounts),
                new AnalysisSummaryResponseDto.SwearWordUsage(
                        swearWordCount,
                        ratePercent(swearWordCount, transcribedWordCount)
                ),
                speakingSpeed(transcribedSyllableCount, speakingDurationMs),
                history
        );
    }

    private AnalysisReport readReport(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, AnalysisReport.class);
        } catch (Exception exception) {
            throw new IllegalStateException("개인화 요약을 위한 분석 보고서를 읽을 수 없습니다.", exception);
        }
    }

    private SpeakerRole selfRole(
            AnalysisScenario scenario,
            ParentChildRole parentChildRole
    ) {
        if (scenario != AnalysisScenario.PARENT_CHILD_CONFLICT) {
            return SpeakerRole.SELF;
        }
        if (parentChildRole == null) {
            throw new IllegalStateException("부모·자녀 분석의 본인 역할이 없습니다.");
        }
        return parentChildRole == ParentChildRole.PARENT
                ? SpeakerRole.PARENT
                : SpeakerRole.CHILD;
    }

    private SpeakerMetrics selfMetrics(AnalysisReport report, SpeakerRole selfRole) {
        return report.quantitativeAnalysis().speakers().stream()
                .filter(metrics -> metrics.speakerRole() == selfRole)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("본인 화자의 정량 지표가 없습니다."));
    }

    private void collectFrequentExpressions(
            AnalysisReport report,
            SpeakerRole selfRole,
            Map<String, Long> expressionCounts
    ) {
        report.qualitativeAnalysis().speakerInsights().stream()
                .filter(insight -> insight.speakerRole() == selfRole)
                .flatMap(insight -> insight.frequentExpressions().stream())
                .forEach(expression -> expressionCounts.merge(
                        expression.expression(),
                        (long) expression.count(),
                        Long::sum
                ));
    }

    private long collectSwearWordCount(AnalysisReport report, SpeakerRole selfRole) {
        return report.qualitativeAnalysis().spicinessInsights().stream()
                .filter(insight -> insight.speakerRole() == selfRole)
                .flatMap(insight -> insight.swearWords().stream())
                .mapToLong(QualitativeAnalysis.SwearWordUsage::count)
                .sum();
    }

    private List<AnalysisSummaryResponseDto.FrequentExpression> frequentExpressions(
            Map<String, Long> expressionCounts
    ) {
        return expressionCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(FREQUENT_EXPRESSION_LIMIT)
                .map(entry -> new AnalysisSummaryResponseDto.FrequentExpression(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    private BigDecimal ratePercent(long count, long wordCount) {
        if (wordCount == 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(wordCount), 2, RoundingMode.HALF_UP);
    }

    private AnalysisSummaryResponseDto.SpeakingSpeed speakingSpeed(
            long syllableCount,
            long durationMs
    ) {
        if (syllableCount == 0 || durationMs == 0) {
            return null;
        }
        BigDecimal syllablesPerSecond = BigDecimal.valueOf(syllableCount)
                .multiply(MILLIS_PER_SECOND)
                .divide(BigDecimal.valueOf(durationMs), 2, RoundingMode.HALF_UP);
        BigDecimal differencePercent = syllablesPerSecond
                .subtract(REFERENCE_SYLLABLES_PER_SECOND)
                .multiply(ONE_HUNDRED)
                .divide(REFERENCE_SYLLABLES_PER_SECOND, 2, RoundingMode.HALF_UP);
        AnalysisSummaryResponseDto.SpeakingSpeedLevel level = speakingSpeedLevel(
                syllablesPerSecond
        );
        return new AnalysisSummaryResponseDto.SpeakingSpeed(
                syllablesPerSecond,
                REFERENCE_SYLLABLES_PER_SECOND,
                differencePercent,
                level,
                speakingSpeedDescription(level, differencePercent)
        );
    }

    private AnalysisSummaryResponseDto.SpeakingSpeedLevel speakingSpeedLevel(
            BigDecimal syllablesPerSecond
    ) {
        if (syllablesPerSecond.compareTo(RELAXED_UPPER_BOUND) < 0) {
            return AnalysisSummaryResponseDto.SpeakingSpeedLevel.RELAXED;
        }
        if (syllablesPerSecond.compareTo(TYPICAL_UPPER_BOUND) <= 0) {
            return AnalysisSummaryResponseDto.SpeakingSpeedLevel.TYPICAL;
        }
        return AnalysisSummaryResponseDto.SpeakingSpeedLevel.FAST;
    }

    private String speakingSpeedDescription(
            AnalysisSummaryResponseDto.SpeakingSpeedLevel level,
            BigDecimal differencePercent
    ) {
        return switch (level) {
            case RELAXED -> "한국인 평균보다 약 "
                    + differencePercent.abs().setScale(0, RoundingMode.HALF_UP)
                    + "% 여유롭게 말해요.";
            case TYPICAL -> "한국인 평균과 비슷한 속도로 말해요.";
            case FAST -> "한국인 평균보다 약 "
                    + differencePercent.abs().setScale(0, RoundingMode.HALF_UP)
                    + "% 빠르게 말해요.";
        };
    }
}
