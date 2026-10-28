package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LinerAnalysisResponseGrounder {

    private static final int MAX_PUBLIC_EXPRESSION_WORDS = 2;
    private static final int MAX_PUBLIC_EXPRESSION_LENGTH = 10;
    private static final Set<String> LOW_VALUE_EXPRESSIONS = Set.of(
            "나", "내가", "너", "네가", "우리", "저", "제가",
            "거", "것", "이거", "그거", "저거"
    );

    public QualitativeAnalysis ground(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        if (source == null || response == null) {
            return response;
        }

        List<QualitativeAnalysis.SpeakerInsight> groundedSpeakerInsights = response.speakerInsights() == null
                ? null
                : response.speakerInsights().stream()
                .map(insight -> groundSpeakerInsight(source, insight))
                .toList();
        List<QualitativeAnalysis.SpicinessInsight> groundedSpicinessInsights =
                response.spicinessInsights() == null
                        ? null
                        : response.spicinessInsights().stream()
                        .map(insight -> groundSpicinessInsight(source, insight))
                        .toList();

        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                groundTopics(response.topics()),
                response.characterInsights(),
                groundedSpeakerInsights,
                response.interestInsights(),
                groundedSpicinessInsights,
                response.reactionStyleInsights(),
                response.scenarioInsights(),
                groundRepresentativeEvidences(source, response.representativeEvidences()),
                response.relationshipSummary(),
                groundReportMetrics(response.reportMetrics()),
                response.speakerTendencies(),
                response.coachingSuggestions()
        );
    }

    private List<QualitativeAnalysis.ReportMetric> groundReportMetrics(
            List<QualitativeAnalysis.ReportMetric> metrics
    ) {
        if (metrics == null) {
            return null;
        }
        return metrics.stream()
                .map(metric -> {
                    if (metric == null
                            || metric.unit() != QualitativeAnalysis.ReportMetricUnit.COUNT
                            || metric.evidenceSegmentIds() == null) {
                        return metric;
                    }
                    return new QualitativeAnalysis.ReportMetric(
                            metric.type(),
                            metric.speakerRole(),
                            (int) metric.evidenceSegmentIds().stream().distinct().count(),
                            metric.unit(),
                            metric.title(),
                            metric.description(),
                            metric.evidenceSegmentIds()
                    );
                })
                .toList();
    }

    private List<QualitativeAnalysis.RepresentativeEvidence> groundRepresentativeEvidences(
            AnalysisSource source,
            List<QualitativeAnalysis.RepresentativeEvidence> evidences
    ) {
        if (evidences == null) {
            return null;
        }

        Map<Long, AnalysisSegment> segmentsById = source.segments().stream()
                .collect(java.util.stream.Collectors.toMap(
                        AnalysisSegment::segmentId,
                        segment -> segment
                ));
        return evidences.stream()
                .map(evidence -> groundRepresentativeEvidence(
                        evidence,
                        segmentsById,
                        source.scenario() == AnalysisScenario.FRIEND_DAILY
                                || source.scenario() == AnalysisScenario.COUPLE_DAILY
                ))
                .toList();
    }

    private QualitativeAnalysis.RepresentativeEvidence groundRepresentativeEvidence(
            QualitativeAnalysis.RepresentativeEvidence evidence,
            Map<Long, AnalysisSegment> segmentsById,
            boolean dailyScenario
    ) {
        if (evidence == null || evidence.segmentId() == null) {
            return evidence;
        }
        AnalysisSegment segment = segmentsById.get(evidence.segmentId());
        if (segment == null) {
            return evidence;
        }

        String candidate = stripOuterQuotes(evidence.displayContent());
        String displayContent = isConservativeCorrection(segment.content(), candidate)
                ? candidate
                : segment.content();
        return new QualitativeAnalysis.RepresentativeEvidence(
                evidence.type(),
                evidence.speakerRole(),
                dailyScenario ? evidence.reactionCategory() : null,
                evidence.segmentId(),
                displayContent
        );
    }

    private String stripOuterQuotes(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        String trimmed = value.trim();
        if (trimmed.length() >= 2
                && ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("“") && trimmed.endsWith("”")))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private boolean isConservativeCorrection(String original, String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return false;
        }
        String normalizedOriginal = normalizeForComparison(original);
        String normalizedCandidate = normalizeForComparison(candidate);
        if (normalizedOriginal.isEmpty() || normalizedCandidate.isEmpty()) {
            return false;
        }
        double lengthRatio = (double) normalizedCandidate.length() / normalizedOriginal.length();
        if (lengthRatio < 0.5 || lengthRatio > 1.5) {
            return false;
        }
        int maxLength = Math.max(normalizedOriginal.length(), normalizedCandidate.length());
        return (double) levenshteinDistance(normalizedOriginal, normalizedCandidate) / maxLength <= 0.45;
    }

    private String normalizeForComparison(String value) {
        return value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]", "");
    }

    private int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) {
            previous[index] = index;
        }
        for (int leftIndex = 1; leftIndex <= left.length(); leftIndex++) {
            current[0] = leftIndex;
            for (int rightIndex = 1; rightIndex <= right.length(); rightIndex++) {
                int substitutionCost = left.charAt(leftIndex - 1) == right.charAt(rightIndex - 1)
                        ? 0
                        : 1;
                current[rightIndex] = Math.min(
                        Math.min(current[rightIndex - 1] + 1, previous[rightIndex] + 1),
                        previous[rightIndex - 1] + substitutionCost
                );
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private List<QualitativeAnalysis.Topic> groundTopics(
            List<QualitativeAnalysis.Topic> topics
    ) {
        if (topics == null) {
            return null;
        }

        Set<Long> assignedSegmentIds = new LinkedHashSet<>();
        List<QualitativeAnalysis.Topic> groundedTopics = new ArrayList<>();
        for (QualitativeAnalysis.Topic topic : topics) {
            if (topic == null) {
                groundedTopics.add(null);
                continue;
            }
            QualitativeAnalysis.Topic groundedTopic = groundTopic(topic, assignedSegmentIds);
            if (groundedTopic != null) {
                groundedTopics.add(groundedTopic);
            }
        }
        return List.copyOf(groundedTopics);
    }

    private QualitativeAnalysis.Topic groundTopic(
            QualitativeAnalysis.Topic topic,
            Set<Long> assignedSegmentIds
    ) {
        if (topic == null || topic.segmentIds() == null) {
            return topic;
        }

        List<Long> uniqueSegmentIds = topic.segmentIds().stream()
                .filter(assignedSegmentIds::add)
                .toList();
        if (uniqueSegmentIds.isEmpty()) {
            return null;
        }

        return new QualitativeAnalysis.Topic(
                topic.title(),
                topic.description(),
                uniqueSegmentIds
        );
    }

    private QualitativeAnalysis.SpeakerInsight groundSpeakerInsight(
            AnalysisSource source,
            QualitativeAnalysis.SpeakerInsight insight
    ) {
        if (insight == null || insight.frequentExpressions() == null) {
            return insight;
        }

        List<QualitativeAnalysis.FrequentExpression> groundedExpressions =
                insight.frequentExpressions().stream()
                        .map(expression -> groundExpression(
                                source.segments(),
                                insight.speakerRole(),
                                expression
                        ))
                        .filter(Objects::nonNull)
                        .toList();

        return new QualitativeAnalysis.SpeakerInsight(
                insight.speakerRole(),
                insight.patterns(),
                groundedExpressions.isEmpty()
                        ? null
                        : groundFrequentExpressionSummary(
                                insight.frequentExpressionSummary(),
                                groundedExpressions
                        ),
                groundedExpressions
        );
    }

    private QualitativeAnalysis.FrequentExpressionSummary groundFrequentExpressionSummary(
            QualitativeAnalysis.FrequentExpressionSummary summary,
            List<QualitativeAnalysis.FrequentExpression> expressions
    ) {
        if (summary == null) {
            return null;
        }

        LinkedHashSet<Long> evidenceSegmentIds = new LinkedHashSet<>();
        expressions.forEach(expression ->
                evidenceSegmentIds.addAll(expression.evidenceSegmentIds()));
        return new QualitativeAnalysis.FrequentExpressionSummary(
                summary.title(),
                summary.description(),
                List.copyOf(evidenceSegmentIds)
        );
    }

    private QualitativeAnalysis.FrequentExpression groundExpression(
            List<AnalysisSegment> segments,
            SpeakerRole speakerRole,
            QualitativeAnalysis.FrequentExpression expression
    ) {
        if (expression == null || !StringUtils.hasText(expression.expression())) {
            return expression;
        }
        if (expression.category() != null && !isPublicExpressionCandidate(expression)) {
            return null;
        }

        List<AnalysisSegment> matchingSegments = segments.stream()
                .filter(segment -> segment.speakerRole() == speakerRole)
                .filter(segment -> segment.content().contains(expression.expression()))
                .toList();
        int occurrenceCount = matchingSegments.stream()
                .mapToInt(segment -> countOccurrences(
                        segment.content(),
                        expression.expression()
                ))
                .sum();

        if (occurrenceCount < 2) {
            return null;
        }

        return new QualitativeAnalysis.FrequentExpression(
                expression.category(),
                expression.expression(),
                occurrenceCount,
                matchingSegments.stream().map(AnalysisSegment::segmentId).toList()
        );
    }

    private QualitativeAnalysis.SpicinessInsight groundSpicinessInsight(
            AnalysisSource source,
            QualitativeAnalysis.SpicinessInsight insight
    ) {
        if (insight == null || insight.swearWords() == null) {
            return insight;
        }

        List<QualitativeAnalysis.SwearWordUsage> groundedSwearWords = insight.swearWords().stream()
                .map(swearWord -> groundSwearWord(
                        source.segments(),
                        insight.speakerRole(),
                        swearWord
                ))
                .filter(Objects::nonNull)
                .toList();
        List<QualitativeAnalysis.SpicinessObservation> groundedObservations =
                insight.observations() == null
                        ? null
                        : insight.observations().stream()
                        .filter(observation -> observation == null
                                || observation.category()
                                != QualitativeAnalysis.SpicinessCategory.SWEAR_WORD
                                || !groundedSwearWords.isEmpty())
                        .toList();

        return new QualitativeAnalysis.SpicinessInsight(
                insight.speakerRole(),
                insight.score(),
                insight.description(),
                groundedSwearWords,
                groundedObservations
        );
    }

    private QualitativeAnalysis.SwearWordUsage groundSwearWord(
            List<AnalysisSegment> segments,
            SpeakerRole speakerRole,
            QualitativeAnalysis.SwearWordUsage swearWord
    ) {
        if (swearWord == null || !StringUtils.hasText(swearWord.expression())) {
            return null;
        }

        String expression = swearWord.expression().trim();
        List<AnalysisSegment> matchingSegments = segments.stream()
                .filter(segment -> segment.speakerRole() == speakerRole)
                .filter(segment -> segment.content().contains(expression))
                .toList();
        int occurrenceCount = matchingSegments.stream()
                .mapToInt(segment -> countOccurrences(segment.content(), expression))
                .sum();
        if (occurrenceCount == 0) {
            return null;
        }

        return new QualitativeAnalysis.SwearWordUsage(
                expression,
                occurrenceCount,
                matchingSegments.stream().map(AnalysisSegment::segmentId).toList()
        );
    }

    private int countOccurrences(String content, String expression) {
        int count = 0;
        int index = 0;
        while ((index = content.indexOf(expression, index)) >= 0) {
            count++;
            index += expression.length();
        }
        return count;
    }

    private boolean isPublicExpressionCandidate(
            QualitativeAnalysis.FrequentExpression expression
    ) {
        String value = expression.expression().trim();
        if (LOW_VALUE_EXPRESSIONS.contains(value)) {
            return false;
        }
        int wordCount = value.split("\\s+").length;
        int maxWords = expression.category()
                == QualitativeAnalysis.FrequentExpressionCategory.WORD
                ? 1
                : MAX_PUBLIC_EXPRESSION_WORDS;
        String comparableValue = value.replaceAll("[\\p{P}\\p{S}\\s]", "");
        int characterCount = comparableValue.codePointCount(0, comparableValue.length());
        return wordCount <= maxWords
                && characterCount > 0
                && characterCount <= MAX_PUBLIC_EXPRESSION_LENGTH;
    }
}
