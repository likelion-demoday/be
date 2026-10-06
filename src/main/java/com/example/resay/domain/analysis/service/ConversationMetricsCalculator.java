package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

// 실제 계산 담당
@Component
public class ConversationMetricsCalculator {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MILLIS_PER_MINUTE = BigDecimal.valueOf(60_000);
    private static final BigDecimal MILLIS_PER_SECOND = BigDecimal.valueOf(1_000);
    private static final int SCALE = 2;

    public ConversationMetrics calculate(AnalysisSource source) {
        if (source == null) {
            throw new IllegalArgumentException("분석 입력은 비어 있을 수 없습니다.");
        }

        Map<SpeakerRole, Accumulator> accumulators = new EnumMap<>(SpeakerRole.class);
        for (AnalysisSegment segment : source.segments()) {
            Accumulator accumulator = accumulators.computeIfAbsent(
                    segment.speakerRole(),
                    ignored -> new Accumulator()
            );
            accumulator.add(segment);
        }

        long totalSpeakingDurationMs = accumulators.values().stream()
                .mapToLong(Accumulator::speakingDurationMs)
                .sum();
        Map<SpeakerRole, SpeakerMetrics> metrics = new EnumMap<>(SpeakerRole.class);
        for (Map.Entry<SpeakerRole, Accumulator> entry : accumulators.entrySet()) {
            metrics.put(
                    entry.getKey(),
                    entry.getValue().toMetrics(entry.getKey(), totalSpeakingDurationMs)
            );
        }
        return new ConversationMetrics(metrics);
    }

    private static long countTranscribedCharacters(String content) {
        return content.codePoints()
                .filter(Character::isLetterOrDigit)
                .count();
    }

    private static long countTranscribedWords(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return java.util.Arrays.stream(content.strip().split("\\s+"))
                .filter(word -> word.codePoints().anyMatch(Character::isLetterOrDigit))
                .count();
    }

    private static long countTranscribedSyllables(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return content.codePoints()
                .filter(codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                .count();
    }

    private static BigDecimal divide(long dividend, long divisor) {
        return BigDecimal.valueOf(dividend)
                .divide(BigDecimal.valueOf(divisor), SCALE, RoundingMode.HALF_UP);
    }

    private static final class Accumulator {

        private long speakingDurationMs;
        private int utteranceCount;
        private long transcribedCharacterCount;
        private long transcribedSyllableCount;
        private long transcribedWordCount;

        private void add(AnalysisSegment segment) {
            speakingDurationMs += segment.endMs() - segment.startMs();
            utteranceCount++;
            transcribedCharacterCount += countTranscribedCharacters(segment.content());
            transcribedSyllableCount += countTranscribedSyllables(segment.content());
            transcribedWordCount += countTranscribedWords(segment.content());
        }

        private long speakingDurationMs() {
            return speakingDurationMs;
        }

        private SpeakerMetrics toMetrics(SpeakerRole speakerRole, long totalSpeakingDurationMs) {
            BigDecimal speakingRatioPercent = BigDecimal.valueOf(speakingDurationMs)
                    .multiply(ONE_HUNDRED)
                    .divide(
                            BigDecimal.valueOf(totalSpeakingDurationMs),
                            SCALE,
                            RoundingMode.HALF_UP
                    );
            BigDecimal averageUtteranceDurationMs = divide(
                    speakingDurationMs,
                    utteranceCount
            );
            BigDecimal charactersPerMinute = BigDecimal.valueOf(transcribedCharacterCount)
                    .multiply(MILLIS_PER_MINUTE)
                    .divide(
                            BigDecimal.valueOf(speakingDurationMs),
                            SCALE,
                            RoundingMode.HALF_UP
                    );
            BigDecimal syllablesPerSecond = BigDecimal.valueOf(transcribedSyllableCount)
                    .multiply(MILLIS_PER_SECOND)
                    .divide(
                            BigDecimal.valueOf(speakingDurationMs),
                            SCALE,
                            RoundingMode.HALF_UP
                    );

            return new SpeakerMetrics(
                    speakerRole,
                    speakingDurationMs,
                    utteranceCount,
                    transcribedCharacterCount,
                    transcribedSyllableCount,
                    transcribedWordCount,
                    speakingRatioPercent,
                    averageUtteranceDurationMs,
                    charactersPerMinute,
                    syllablesPerSecond
            );
        }
    }
}
