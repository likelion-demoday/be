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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
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
                groundRepresentativeEvidences(
                        source,
                        response.reactionStyleInsights(),
                        response.representativeEvidences()
                ),
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
            List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
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
        boolean dailyScenario = source.scenario() == AnalysisScenario.FRIEND_DAILY
                || source.scenario() == AnalysisScenario.COUPLE_DAILY;
        List<QualitativeAnalysis.RepresentativeEvidence> grounded = evidences.stream()
                .map(evidence -> groundRepresentativeEvidence(
                        evidence,
                        segmentsById,
                        dailyScenario
                ))
                .toList();
        if (!dailyScenario || grounded.isEmpty() || reactionStyleInsights == null) {
            return grounded;
        }

        List<QualitativeAnalysis.RepresentativeEvidence> reconciled =
                reconcileDailyRepresentativeEvidences(
                        source,
                        reactionStyleInsights,
                        grounded,
                        segmentsById
                );
        if (!reconciled.equals(grounded)) {
            log.info(
                    "T/F 대표 근거 정합성 복구: recordingId={}, 기존 {}건, 복구 후 {}건",
                    source.recordingId(),
                    grounded.size(),
                    reconciled.size()
            );
        }
        return reconciled;
    }

    private List<QualitativeAnalysis.RepresentativeEvidence>
            reconcileDailyRepresentativeEvidences(
                    AnalysisSource source,
                    List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
                    List<QualitativeAnalysis.RepresentativeEvidence> evidences,
                    Map<Long, AnalysisSegment> segmentsById
            ) {
        List<QualitativeAnalysis.RepresentativeEvidence> reconciled = new ArrayList<>();
        Set<String> selected = new LinkedHashSet<>();
        Map<SpeakerRole, Integer> counts = new java.util.EnumMap<>(SpeakerRole.class);

        // 정상 근거를 먼저 보존한 뒤 남은 자리만 잘못된 근거의 대체 발화로 채운다.
        for (QualitativeAnalysis.RepresentativeEvidence evidence : evidences) {
            if (evidence == null
                    || !source.scenario().requiredRoles().contains(evidence.speakerRole())
                    || counts.getOrDefault(evidence.speakerRole(), 0) >= 3) {
                continue;
            }
            QualitativeAnalysis.ReactionStyleInsight insight = reactionStyleInsights.stream()
                    .filter(candidate -> candidate != null
                            && candidate.speakerRole() == evidence.speakerRole())
                    .findFirst()
                    .orElse(null);
            QualitativeAnalysis.ReactionExample example = findReactionExample(
                    insight,
                    evidence.reactionCategory()
            );
            if (example == null
                    || !isValidExampleSegment(
                            example,
                            evidence.speakerRole(),
                            evidence.segmentId(),
                            segmentsById
                    )
                    || !selectIfValid(
                            evidence.segmentId(),
                            evidence.speakerRole(),
                            example.category(),
                            segmentsById,
                            selected
                    )) {
                continue;
            }
            AnalysisSegment segment = segmentsById.get(evidence.segmentId());
            String displayContent = StringUtils.hasText(evidence.displayContent())
                    ? evidence.displayContent()
                    : segment.content();
            reconciled.add(new QualitativeAnalysis.RepresentativeEvidence(
                    QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                    evidence.speakerRole(),
                    example.category(),
                    evidence.segmentId(),
                    displayContent
            ));
            counts.merge(evidence.speakerRole(), 1, Integer::sum);
        }

        for (QualitativeAnalysis.RepresentativeEvidence evidence : evidences) {
            if (evidence == null
                    || !source.scenario().requiredRoles().contains(evidence.speakerRole())
                    || counts.getOrDefault(evidence.speakerRole(), 0) >= 3) {
                continue;
            }
            QualitativeAnalysis.ReactionStyleInsight insight = reactionStyleInsights.stream()
                    .filter(candidate -> candidate != null
                            && candidate.speakerRole() == evidence.speakerRole())
                    .findFirst()
                    .orElse(null);
            QualitativeAnalysis.ReactionExample example = findReactionExample(
                    insight,
                    evidence.reactionCategory()
            );
            if (example == null || isValidExampleSegment(
                    example,
                    evidence.speakerRole(),
                    evidence.segmentId(),
                    segmentsById
            )) {
                continue;
            }
            Long segmentId = selectAvailableSegmentId(
                    example,
                    evidence.speakerRole(),
                    segmentsById,
                    selected
            );
            if (segmentId == null) {
                continue;
            }
            reconciled.add(new QualitativeAnalysis.RepresentativeEvidence(
                    QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                    evidence.speakerRole(),
                    example.category(),
                    segmentId,
                    segmentsById.get(segmentId).content()
            ));
            counts.merge(evidence.speakerRole(), 1, Integer::sum);
        }

        // LINER가 한 화자의 대표 근거를 모두 잘못 고른 경우에도 반응 예시에서 한 건을 복구한다.
        for (QualitativeAnalysis.ReactionStyleInsight insight : reactionStyleInsights) {
            if (insight == null
                    || !source.scenario().requiredRoles().contains(insight.speakerRole())
                    || counts.getOrDefault(insight.speakerRole(), 0) > 0
                    || insight.examples() == null) {
                continue;
            }
            for (QualitativeAnalysis.ReactionExample example : insight.examples()) {
                Long segmentId = selectAvailableSegmentId(
                        example,
                        insight.speakerRole(),
                        segmentsById,
                        selected
                );
                if (segmentId == null) {
                    continue;
                }
                reconciled.add(new QualitativeAnalysis.RepresentativeEvidence(
                        QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE,
                        insight.speakerRole(),
                        example.category(),
                        segmentId,
                        segmentsById.get(segmentId).content()
                ));
                counts.put(insight.speakerRole(), 1);
                break;
            }
        }
        return List.copyOf(reconciled);
    }

    private QualitativeAnalysis.ReactionExample findReactionExample(
            QualitativeAnalysis.ReactionStyleInsight insight,
            QualitativeAnalysis.ReactionCategory category
    ) {
        if (insight == null || insight.examples() == null || category == null) {
            return null;
        }
        return insight.examples().stream()
                .filter(example -> example != null && example.category() == category)
                .findFirst()
                .orElse(null);
    }

    private Long selectAvailableSegmentId(
            QualitativeAnalysis.ReactionExample example,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById,
            Set<String> selected
    ) {
        if (example == null || example.category() == null || example.evidenceSegmentIds() == null) {
            return null;
        }
        return example.evidenceSegmentIds().stream()
                .filter(segmentId -> selectIfValid(
                        segmentId,
                        speakerRole,
                        example.category(),
                        segmentsById,
                        selected
                ))
                .findFirst()
                .orElse(null);
    }

    private boolean selectIfValid(
            Long segmentId,
            SpeakerRole speakerRole,
            QualitativeAnalysis.ReactionCategory category,
            Map<Long, AnalysisSegment> segmentsById,
            Set<String> selected
    ) {
        AnalysisSegment segment = segmentsById.get(segmentId);
        if (segment == null || segment.speakerRole() != speakerRole) {
            return false;
        }
        return selected.add(speakerRole + ":" + category + ":" + segmentId);
    }

    private boolean isValidExampleSegment(
            QualitativeAnalysis.ReactionExample example,
            SpeakerRole speakerRole,
            Long segmentId,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (example.evidenceSegmentIds() == null
                || !example.evidenceSegmentIds().contains(segmentId)) {
            return false;
        }
        AnalysisSegment segment = segmentsById.get(segmentId);
        return segment != null && segment.speakerRole() == speakerRole;
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
