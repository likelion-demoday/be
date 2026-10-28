package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.service.AnalysisFailureCategory;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.CharacterInsightTextPolicy;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LinerAnalysisResponseValidator {

    public void validate(AnalysisSource source, QualitativeAnalysis response) {
        if (source == null || response == null) {
            throw invalidResponse("분석 입력 또는 응답이 비어 있습니다.");
        }

        Map<Long, AnalysisSegment> segmentsById = segmentsById(source.segments());
        validateOverview(response.overview(), segmentsById);
        validateTimeline(response.timeline(), segmentsById);
        validateTopics(response.topics(), segmentsById);
        validateCharacterInsights(source, response.characterInsights(), segmentsById);
        validateSpeakerInsights(source, response.speakerInsights(), segmentsById);
        validateInterestInsights(source, response.interestInsights(), segmentsById);
        validateSpicinessInsights(source, response.spicinessInsights(), segmentsById);
        validateReactionStyleInsights(source, response.reactionStyleInsights(), segmentsById);
        validateScenarioInsights(source, response.scenarioInsights(), segmentsById);
        validateExtendedReportFields(source, response, segmentsById);
        validateRepresentativeEvidences(source, response, segmentsById);
    }

    private void validateRepresentativeEvidences(
            AnalysisSource source,
            QualitativeAnalysis response,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        List<QualitativeAnalysis.RepresentativeEvidence> evidences =
                response.representativeEvidences();
        // 저장된 구버전 fixture 및 보고서와의 호환을 위한 처리다. 신규 LINER 스키마에서는 필수다.
        if (evidences == null || evidences.isEmpty()) {
            return;
        }

        Set<Long> analysisEvidenceIds = collectAnalysisEvidenceIds(response);
        Set<String> uniqueSelections = new HashSet<>();
        Map<Long, String> displayContentBySegmentId = new HashMap<>();
        for (QualitativeAnalysis.RepresentativeEvidence evidence : evidences) {
            if (evidence == null || evidence.type() == null || evidence.speakerRole() == null) {
                throw invalidResponse("representativeEvidence 항목이 올바르지 않습니다.");
            }
            requireText(evidence.displayContent(), "representativeEvidence.displayContent");
            AnalysisSegment segment = segmentsById.get(evidence.segmentId());
            if (segment == null) {
                throw invalidResponse("대표 근거에 존재하지 않는 발화가 포함되어 있습니다.");
            }
            if (segment.speakerRole() != evidence.speakerRole()) {
                throw invalidResponse("대표 근거의 화자가 원본 발화와 일치하지 않습니다.");
            }
            if (!analysisEvidenceIds.contains(evidence.segmentId())) {
                throw invalidResponse("대표 근거는 분석 항목의 근거 발화에 포함되어야 합니다.");
            }
            String uniqueKey = evidence.type() + ":" + evidence.speakerRole() + ":"
                    + evidence.reactionCategory() + ":" + evidence.segmentId();
            if (!uniqueSelections.add(uniqueKey)) {
                throw invalidResponse("같은 대표 근거를 중복해서 작성할 수 없습니다.");
            }
            String previousContent = displayContentBySegmentId.putIfAbsent(
                    evidence.segmentId(),
                    evidence.displayContent()
            );
            if (previousContent != null && !previousContent.equals(evidence.displayContent())) {
                throw invalidResponse("같은 발화에는 동일한 표시 문장을 사용해야 합니다.");
            }
        }

        switch (source.scenario()) {
            case FRIEND_DAILY, COUPLE_DAILY -> validateDailyRepresentativeEvidences(
                    source,
                    response.reactionStyleInsights(),
                    evidences
            );
            case COUPLE_CONFLICT -> validateCoupleConflictRepresentativeEvidences(
                    source,
                    evidences
            );
            case PARENT_CHILD_CONFLICT -> validateParentChildRepresentativeEvidences(evidences);
        }
    }

    private void validateDailyRepresentativeEvidences(
            AnalysisSource source,
            List<QualitativeAnalysis.ReactionStyleInsight> reactionStyleInsights,
            List<QualitativeAnalysis.RepresentativeEvidence> evidences
    ) {
        if (evidences.stream().anyMatch(evidence ->
                evidence.type() != QualitativeAnalysis.ReportEvidenceType.REACTION_STYLE_EXAMPLE
                        || evidence.reactionCategory() == null)) {
            throw invalidResponse("일상 대화의 대표 근거는 T/F 반응 예시여야 합니다.");
        }

        for (SpeakerRole role : source.scenario().requiredRoles()) {
            List<QualitativeAnalysis.RepresentativeEvidence> roleEvidences = evidences.stream()
                    .filter(evidence -> evidence.speakerRole() == role)
                    .toList();
            if (roleEvidences.isEmpty() || roleEvidences.size() > 3) {
                throw invalidResponse("일상 대화의 대표 반응은 화자별 1개 이상 3개 이하여야 합니다.");
            }
            QualitativeAnalysis.ReactionStyleInsight insight = reactionStyleInsights.stream()
                    .filter(candidate -> candidate.speakerRole() == role)
                    .findFirst()
                    .orElseThrow();
            for (QualitativeAnalysis.RepresentativeEvidence evidence : roleEvidences) {
                boolean linkedToExample = insight.examples().stream().anyMatch(example ->
                        example.category() == evidence.reactionCategory()
                                && example.evidenceSegmentIds().contains(evidence.segmentId()));
                if (!linkedToExample) {
                    throw invalidResponse("T/F 대표 근거가 해당 반응 예시의 근거와 일치하지 않습니다.");
                }
            }
        }
    }

    private void validateCoupleConflictRepresentativeEvidences(
            AnalysisSource source,
            List<QualitativeAnalysis.RepresentativeEvidence> evidences
    ) {
        if (evidences.size() != 6 || evidences.stream()
                .anyMatch(evidence -> evidence.reactionCategory() != null)) {
            throw invalidResponse("연인 갈등의 대표 근거는 정해진 6개 항목이어야 합니다.");
        }
        requireEvidenceTypeCount(evidences,
                QualitativeAnalysis.ReportEvidenceType.EMOTION_EXPRESSION, 1);
        requireEvidenceTypeCount(evidences,
                QualitativeAnalysis.ReportEvidenceType.MISSED_QUESTION, 1);
        requireEvidenceTypeCount(evidences,
                QualitativeAnalysis.ReportEvidenceType.FOLLOWING_RESPONSE, 1);
        requireEvidenceTypeCount(evidences,
                QualitativeAnalysis.ReportEvidenceType.RECOVERY_START, 1);
        requireEvidenceTypeCount(evidences,
                QualitativeAnalysis.ReportEvidenceType.SPEAKER_REACTION, 2);

        Set<SpeakerRole> reactionRoles = evidences.stream()
                .filter(evidence -> evidence.type()
                        == QualitativeAnalysis.ReportEvidenceType.SPEAKER_REACTION)
                .map(QualitativeAnalysis.RepresentativeEvidence::speakerRole)
                .collect(Collectors.toSet());
        if (!reactionRoles.equals(source.scenario().requiredRoles())) {
            throw invalidResponse("연인 갈등의 대표 반응은 두 화자에 대해 각각 필요합니다.");
        }
    }

    private void validateParentChildRepresentativeEvidences(
            List<QualitativeAnalysis.RepresentativeEvidence> evidences
    ) {
        if (evidences.size() != 6 || evidences.stream()
                .anyMatch(evidence -> evidence.reactionCategory() != null)) {
            throw invalidResponse("부모 자녀 갈등의 대표 근거는 정해진 6개 항목이어야 합니다.");
        }
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.CHILD_POSITION, SpeakerRole.CHILD);
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.FOLLOWING_ADVICE, SpeakerRole.PARENT);
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.UNASKED_QUESTION, SpeakerRole.CHILD);
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.WORRY_START, SpeakerRole.PARENT);
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.EXPRESSION_TURNING_POINT,
                SpeakerRole.PARENT);
        requireEvidenceTypeAndRole(evidences,
                QualitativeAnalysis.ReportEvidenceType.CLOSING_RESPONSE, SpeakerRole.CHILD);
    }

    private void requireEvidenceTypeCount(
            List<QualitativeAnalysis.RepresentativeEvidence> evidences,
            QualitativeAnalysis.ReportEvidenceType type,
            long expectedCount
    ) {
        long actualCount = evidences.stream().filter(evidence -> evidence.type() == type).count();
        if (actualCount != expectedCount) {
            throw invalidResponse(type + " 대표 근거의 개수가 올바르지 않습니다.");
        }
    }

    private void requireEvidenceTypeAndRole(
            List<QualitativeAnalysis.RepresentativeEvidence> evidences,
            QualitativeAnalysis.ReportEvidenceType type,
            SpeakerRole role
    ) {
        requireEvidenceTypeCount(evidences, type, 1);
        boolean matchesRole = evidences.stream()
                .filter(evidence -> evidence.type() == type)
                .allMatch(evidence -> evidence.speakerRole() == role);
        if (!matchesRole) {
            throw invalidResponse(type + " 대표 근거의 화자 역할이 올바르지 않습니다.");
        }
    }

    private Set<Long> collectAnalysisEvidenceIds(QualitativeAnalysis response) {
        Set<Long> result = new HashSet<>();
        result.addAll(response.overview().evidenceSegmentIds());
        response.timeline().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        response.topics().forEach(item -> result.addAll(item.segmentIds()));
        response.characterInsights().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        response.speakerInsights().forEach(insight -> {
            insight.patterns().forEach(item -> result.addAll(item.evidenceSegmentIds()));
            if (insight.frequentExpressionSummary() != null) {
                result.addAll(insight.frequentExpressionSummary().evidenceSegmentIds());
            }
            insight.frequentExpressions().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        });
        response.interestInsights().forEach(insight -> insight.observations()
                .forEach(item -> result.addAll(item.evidenceSegmentIds())));
        response.spicinessInsights().forEach(insight -> {
            insight.swearWords().forEach(item -> result.addAll(item.evidenceSegmentIds()));
            insight.observations().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        });
        response.reactionStyleInsights().forEach(insight -> insight.examples()
                .forEach(item -> result.addAll(item.evidenceSegmentIds())));
        response.scenarioInsights().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        response.reportMetrics().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        return result;
    }

    private void validateExtendedReportFields(
            AnalysisSource source,
            QualitativeAnalysis response,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRelationshipSummary(response.relationshipSummary(), segmentsById);
        validateReportMetrics(source, response.reportMetrics(), segmentsById);
        validateSpeakerTendencies(source, response.speakerTendencies(), segmentsById);
        validateCoachingSuggestions(source, response.coachingSuggestions(), segmentsById);
    }

    private void validateRelationshipSummary(
            QualitativeAnalysis.RelationshipSummary summary,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        // 구버전 fixture와 저장 보고서는 이 필드가 없을 수 있다.
        if (summary == null) {
            return;
        }
        requireText(summary.title(), "relationshipSummary.title");
        requireText(summary.description(), "relationshipSummary.description");
        validateEvidence(summary.evidenceSegmentIds(), segmentsById, "relationshipSummary");
    }

    private void validateReportMetrics(
            AnalysisSource source,
            List<QualitativeAnalysis.ReportMetric> metrics,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (metrics == null || metrics.isEmpty()) {
            return;
        }
        Set<String> uniqueMetrics = new HashSet<>();
        for (QualitativeAnalysis.ReportMetric metric : metrics) {
            if (metric == null || metric.type() == null || metric.unit() == null) {
                throw invalidResponse("reportMetric 항목이 올바르지 않습니다.");
            }
            if (!LinerAnalysisPolicy.reportMetricTypes(source.scenario()).contains(metric.type())) {
                throw invalidResponse("분석 시나리오에 맞지 않는 reportMetric입니다.");
            }
            if (metric.speakerRole() != null
                    && !source.scenario().requiredRoles().contains(metric.speakerRole())) {
                throw invalidResponse("reportMetric의 화자 역할이 올바르지 않습니다.");
            }
            String uniqueKey = metric.type() + ":" + metric.speakerRole() + ":" + metric.unit();
            if (!uniqueMetrics.add(uniqueKey)) {
                throw invalidResponse("같은 reportMetric을 중복해서 작성할 수 없습니다.");
            }
            requireText(metric.title(), "reportMetric.title");
            requireText(metric.description(), "reportMetric.description");
            if (metric.value() < 0) {
                throw invalidResponse("reportMetric.value는 0 이상이어야 합니다.");
            }
            if (metric.unit() == QualitativeAnalysis.ReportMetricUnit.PERCENT
                    && metric.value() > 100) {
                throw invalidResponse("PERCENT reportMetric.value는 100 이하여야 합니다.");
            }
            validateMetricEvidence(metric, segmentsById);
        }
    }

    private void validateMetricEvidence(
            QualitativeAnalysis.ReportMetric metric,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        List<Long> evidenceIds = metric.evidenceSegmentIds();
        if (metric.value() == 0) {
            if (evidenceIds == null || evidenceIds.isEmpty()) {
                return;
            }
            throw invalidResponse("값이 0인 reportMetric에는 근거 발화를 넣을 수 없습니다.");
        }
        validateEvidence(evidenceIds, segmentsById, "reportMetric");
        if (metric.speakerRole() != null && evidenceIds.stream()
                .map(segmentsById::get)
                .anyMatch(segment -> segment.speakerRole() != metric.speakerRole())) {
            throw invalidResponse("reportMetric에는 해당 화자의 근거 발화만 포함해야 합니다.");
        }
        if (metric.unit() == QualitativeAnalysis.ReportMetricUnit.COUNT
                && metric.value() != evidenceIds.stream().distinct().count()) {
            throw invalidResponse("COUNT reportMetric.value는 근거 발화 수와 일치해야 합니다.");
        }
    }

    private void validateSpeakerTendencies(
            AnalysisSource source,
            List<QualitativeAnalysis.SpeakerTendency> tendencies,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (tendencies == null || tendencies.isEmpty()) {
            return;
        }
        validateRoles(
                source,
                tendencies,
                QualitativeAnalysis.SpeakerTendency::speakerRole,
                "speakerTendencies"
        );
        for (QualitativeAnalysis.SpeakerTendency tendency : tendencies) {
            requireText(tendency.label(), "speakerTendency.label");
            requireText(tendency.description(), "speakerTendency.description");
            validateSpeakerEvidence(
                    tendency.evidenceSegmentIds(),
                    tendency.speakerRole(),
                    segmentsById,
                    "speakerTendency"
            );
        }
    }

    private void validateCoachingSuggestions(
            AnalysisSource source,
            List<QualitativeAnalysis.CoachingSuggestion> suggestions,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (suggestions == null || suggestions.isEmpty()) {
            return;
        }
        if (source.scenario() != AnalysisScenario.COUPLE_CONFLICT
                && source.scenario() != AnalysisScenario.PARENT_CHILD_CONFLICT) {
            throw invalidResponse("일상 대화에는 coachingSuggestions를 작성할 수 없습니다.");
        }
        if (suggestions.size() != 3) {
            throw invalidResponse("갈등 대화에는 coachingSuggestions가 세 개 필요합니다.");
        }
        Set<Integer> orders = new HashSet<>();
        for (QualitativeAnalysis.CoachingSuggestion suggestion : suggestions) {
            if (suggestion == null || suggestion.order() < 1 || suggestion.order() > 3
                    || !orders.add(suggestion.order())) {
                throw invalidResponse("coachingSuggestion.order가 올바르지 않습니다.");
            }
            requireText(suggestion.title(), "coachingSuggestion.title");
            requireText(suggestion.description(), "coachingSuggestion.description");
            requireText(suggestion.suggestedPhrase(), "coachingSuggestion.suggestedPhrase");
            validateEvidence(
                    suggestion.evidenceSegmentIds(),
                    segmentsById,
                    "coachingSuggestion"
            );
        }
    }

    private void validateOverview(
            QualitativeAnalysis.Overview overview,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (overview == null) {
            throw invalidResponse("overview가 비어 있습니다.");
        }
        requireText(overview.title(), "overview.title");
        requireText(overview.description(), "overview.description");
        validateEvidence(overview.evidenceSegmentIds(), segmentsById, "overview");
    }

    private void validateTimeline(
            List<QualitativeAnalysis.TimelineItem> timeline,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (timeline == null || timeline.isEmpty() || timeline.size() > 6) {
            throw invalidResponse("timeline은 1개 이상 6개 이하여야 합니다.");
        }

        long previousStartMs = -1;
        for (QualitativeAnalysis.TimelineItem item : timeline) {
            if (item == null) {
                throw invalidResponse("timeline 항목이 비어 있습니다.");
            }
            requireText(item.title(), "timeline.title");
            requireText(item.description(), "timeline.description");
            validateEvidence(item.evidenceSegmentIds(), segmentsById, "timeline");

            long startMs = item.evidenceSegmentIds().stream()
                    .map(segmentsById::get)
                    .mapToLong(AnalysisSegment::startMs)
                    .min()
                    .orElseThrow();
            if (startMs < previousStartMs) {
                throw invalidResponse("timeline은 시간순이어야 합니다.");
            }
            previousStartMs = startMs;
        }
    }

    private void validateTopics(
            List<QualitativeAnalysis.Topic> topics,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (topics == null || topics.isEmpty() || topics.size() > 5) {
            throw invalidResponse("topics는 1개 이상 5개 이하여야 합니다.");
        }
        Set<Long> assignedSegmentIds = new HashSet<>();
        for (QualitativeAnalysis.Topic topic : topics) {
            if (topic == null) {
                throw invalidResponse("topic 항목이 비어 있습니다.");
            }
            requireText(topic.title(), "topic.title");
            requireText(topic.description(), "topic.description");
            validateEvidence(topic.segmentIds(), segmentsById, "topic");
            if (!topic.segmentIds().stream().allMatch(assignedSegmentIds::add)) {
                throw invalidResponse("하나의 발화는 하나의 주제에만 포함되어야 합니다.");
            }
        }
    }

    private void validateCharacterInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.CharacterInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRoles(source, insights, QualitativeAnalysis.CharacterInsight::speakerRole,
                "characterInsights");
        for (QualitativeAnalysis.CharacterInsight insight : insights) {
            requireText(insight.name(), "characterInsight.name");
            requireText(insight.description(), "characterInsight.description");
            requireMaxLength(
                    insight.name(),
                    CharacterInsightTextPolicy.NAME_MAX_LENGTH,
                    "characterInsight.name"
            );
            requireMaxLength(
                    insight.description(),
                    CharacterInsightTextPolicy.DESCRIPTION_MAX_LENGTH,
                    "characterInsight.description"
            );
            validateSpeakerEvidence(
                    insight.evidenceSegmentIds(),
                    insight.speakerRole(),
                    segmentsById,
                    "characterInsight"
            );
        }
    }

    private void validateSpeakerInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.SpeakerInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRoles(source, insights, QualitativeAnalysis.SpeakerInsight::speakerRole,
                "speakerInsights");
        for (QualitativeAnalysis.SpeakerInsight insight : insights) {
            if (insight.patterns() == null || insight.frequentExpressions() == null) {
                throw invalidResponse("speakerInsights 항목이 올바르지 않습니다.");
            }
            for (QualitativeAnalysis.SpeakerPattern pattern : insight.patterns()) {
                validateSpeakerPattern(pattern, insight.speakerRole(), source, segmentsById);
            }
            validateFrequentExpressionSummary(
                    insight.frequentExpressionSummary(),
                    insight.frequentExpressions(),
                    insight.speakerRole(),
                    segmentsById
            );
            validateFrequentExpressions(
                    insight.frequentExpressions(),
                    insight.speakerRole(),
                    segmentsById
            );
        }
    }

    private void validateSpeakerPattern(
            QualitativeAnalysis.SpeakerPattern pattern,
            SpeakerRole speakerRole,
            AnalysisSource source,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (pattern == null || pattern.category() == null) {
            throw invalidResponse("speaker pattern이 올바르지 않습니다.");
        }
        if (!LinerAnalysisPolicy.speakerPatternCategories(source.scenario())
                .contains(pattern.category())) {
            throw invalidResponse("분석 시나리오에 맞지 않는 화자 패턴입니다.");
        }
        requireText(pattern.title(), "speakerPattern.title");
        requireText(pattern.description(), "speakerPattern.description");
        validateSpeakerEvidence(
                pattern.evidenceSegmentIds(),
                speakerRole,
                segmentsById,
                "speakerPattern"
        );
    }

    private void validateFrequentExpressionSummary(
            QualitativeAnalysis.FrequentExpressionSummary summary,
            List<QualitativeAnalysis.FrequentExpression> expressions,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (expressions.isEmpty()) {
            if (summary != null) {
                throw invalidResponse("반복 표현이 없으면 frequentExpressionSummary는 비어 있어야 합니다.");
            }
            return;
        }
        if (summary == null) {
            throw invalidResponse("반복 표현이 있으면 frequentExpressionSummary가 필요합니다.");
        }
        requireText(summary.title(), "frequentExpressionSummary.title");
        requireText(summary.description(), "frequentExpressionSummary.description");
        validateSpeakerEvidence(
                summary.evidenceSegmentIds(),
                speakerRole,
                segmentsById,
                "frequentExpressionSummary"
        );
        Set<Long> expressionEvidenceIds = expressions.stream()
                .flatMap(expression -> expression.evidenceSegmentIds().stream())
                .collect(Collectors.toSet());
        if (!expressionEvidenceIds.containsAll(summary.evidenceSegmentIds())) {
            throw invalidResponse("반복 표현 요약은 실제 반복 표현의 근거만 사용해야 합니다.");
        }
    }

    private void validateFrequentExpressions(
            List<QualitativeAnalysis.FrequentExpression> expressions,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (expressions.size() > 5) {
            throw invalidResponse("frequentExpressions는 5개 이하여야 합니다.");
        }
        for (QualitativeAnalysis.FrequentExpression expression : expressions) {
            if (expression == null || expression.category() == null
                    || expression.count() <= 0) {
                throw invalidResponse("frequentExpression 항목이 올바르지 않습니다.");
            }
            requireText(expression.expression(), "frequentExpression.expression");
            validateSpeakerEvidence(
                    expression.evidenceSegmentIds(),
                    speakerRole,
                    segmentsById,
                    "frequentExpression"
            );
            boolean appearsInEvidence = expression.evidenceSegmentIds().stream()
                    .map(segmentsById::get)
                    .filter(segment -> segment.speakerRole() == speakerRole)
                    .anyMatch(segment -> segment.content().contains(expression.expression()));
            if (!appearsInEvidence) {
                throw invalidResponse("반복 표현이 근거 발화에 실제로 존재하지 않습니다.");
            }

            List<AnalysisSegment> matchingSegments = segmentsById.values().stream()
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
                throw invalidResponse("반복 표현은 해당 화자의 발화에 두 번 이상 존재해야 합니다.");
            }
            Set<Long> matchingSegmentIds = matchingSegments.stream()
                    .map(AnalysisSegment::segmentId)
                    .collect(Collectors.toSet());
            if (!Set.copyOf(expression.evidenceSegmentIds()).containsAll(matchingSegmentIds)) {
                throw invalidResponse("반복 표현의 모든 근거 발화가 포함되어야 합니다.");
            }
        }
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

    private void validateInterestInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.InterestInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRoles(source, insights, QualitativeAnalysis.InterestInsight::speakerRole,
                "interestInsights");
        for (QualitativeAnalysis.InterestInsight insight : insights) {
            requireScore(insight.score(), "interestInsight.score");
            requireText(insight.description(), "interestInsight.description");
            if (insight.observations() == null) {
                throw invalidResponse("interestInsight.observations가 비어 있습니다.");
            }
            for (QualitativeAnalysis.InterestObservation observation : insight.observations()) {
                if (observation == null || observation.category() == null) {
                    throw invalidResponse("interestObservation 항목이 올바르지 않습니다.");
                }
                validateCategorizedEvidence(
                        observation.title(),
                        observation.description(),
                        observation.evidenceSegmentIds(),
                        insight.speakerRole(),
                        segmentsById,
                        "interestObservation"
                );
            }
        }
    }

    private void validateSpicinessInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.SpicinessInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRoles(source, insights, QualitativeAnalysis.SpicinessInsight::speakerRole,
                "spicinessInsights");
        for (QualitativeAnalysis.SpicinessInsight insight : insights) {
            requireScore(insight.score(), "spicinessInsight.score");
            requireText(insight.description(), "spicinessInsight.description");
            if (insight.swearWords() == null || insight.observations() == null) {
                throw invalidResponse("spicinessInsight 항목이 올바르지 않습니다.");
            }
            validateSwearWords(insight.swearWords(), insight.speakerRole(), segmentsById);
            boolean hasSwearWordObservation = insight.observations().stream()
                    .filter(Objects::nonNull)
                    .anyMatch(observation -> observation.category()
                            == QualitativeAnalysis.SpicinessCategory.SWEAR_WORD);
            if (insight.swearWords().isEmpty() != !hasSwearWordObservation) {
                throw invalidResponse("비속어 후보와 SWEAR_WORD 관찰이 일치하지 않습니다.");
            }
            if (!insight.swearWords().isEmpty() && insight.score() == 0) {
                throw invalidResponse("비속어가 있으면 표독력 점수는 0보다 커야 합니다.");
            }
            for (QualitativeAnalysis.SpicinessObservation observation : insight.observations()) {
                if (observation == null || observation.category() == null) {
                    throw invalidResponse("spicinessObservation 항목이 올바르지 않습니다.");
                }
                validateCategorizedEvidence(
                        observation.title(),
                        observation.description(),
                        observation.evidenceSegmentIds(),
                        insight.speakerRole(),
                        segmentsById,
                        "spicinessObservation"
                );
            }
        }
    }

    private void validateSwearWords(
            List<QualitativeAnalysis.SwearWordUsage> swearWords,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (swearWords.size() > 10) {
            throw invalidResponse("swearWords는 10개 이하여야 합니다.");
        }
        Set<String> expressions = new HashSet<>();
        for (QualitativeAnalysis.SwearWordUsage swearWord : swearWords) {
            if (swearWord == null || swearWord.count() <= 0) {
                throw invalidResponse("swearWord 항목이 올바르지 않습니다.");
            }
            requireText(swearWord.expression(), "swearWord.expression");
            if (!expressions.add(swearWord.expression())) {
                throw invalidResponse("같은 비속어 표현을 중복해서 작성할 수 없습니다.");
            }
            validateSpeakerEvidence(
                    swearWord.evidenceSegmentIds(),
                    speakerRole,
                    segmentsById,
                    "swearWord"
            );

            List<AnalysisSegment> matchingSegments = segmentsById.values().stream()
                    .filter(segment -> segment.speakerRole() == speakerRole)
                    .filter(segment -> segment.content().contains(swearWord.expression()))
                    .toList();
            int occurrenceCount = matchingSegments.stream()
                    .mapToInt(segment -> countOccurrences(
                            segment.content(),
                            swearWord.expression()
                    ))
                    .sum();
            if (swearWord.count() != occurrenceCount) {
                throw invalidResponse("비속어 횟수가 실제 전사문의 등장 횟수와 일치하지 않습니다.");
            }
            Set<Long> matchingSegmentIds = matchingSegments.stream()
                    .map(AnalysisSegment::segmentId)
                    .collect(Collectors.toSet());
            if (!Set.copyOf(swearWord.evidenceSegmentIds()).equals(matchingSegmentIds)) {
                throw invalidResponse("비속어가 등장한 모든 근거 발화가 포함되어야 합니다.");
            }
        }
    }

    private void validateReactionStyleInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.ReactionStyleInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        validateRoles(source, insights, QualitativeAnalysis.ReactionStyleInsight::speakerRole,
                "reactionStyleInsights");
        for (QualitativeAnalysis.ReactionStyleInsight insight : insights) {
            requireScore(insight.thinkingPercent(), "reactionStyleInsight.thinkingPercent");
            requireScore(insight.feelingPercent(), "reactionStyleInsight.feelingPercent");
            if (insight.thinkingPercent() + insight.feelingPercent() != 100) {
                throw invalidResponse("T/F 반응 비율의 합은 100이어야 합니다.");
            }
            requireText(insight.description(), "reactionStyleInsight.description");
            if (insight.examples() == null || insight.examples().isEmpty()) {
                throw invalidResponse("reactionStyleInsight.examples는 비어 있을 수 없습니다.");
            }
            for (QualitativeAnalysis.ReactionExample example : insight.examples()) {
                if (example == null || example.category() == null) {
                    throw invalidResponse("reactionExample 항목이 올바르지 않습니다.");
                }
                validateCategorizedEvidence(
                        example.title(),
                        example.description(),
                        example.evidenceSegmentIds(),
                        insight.speakerRole(),
                        segmentsById,
                        "reactionExample"
                );
            }
        }
    }

    private void validateCategorizedEvidence(
            String title,
            String description,
            List<Long> evidenceSegmentIds,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById,
            String fieldName
    ) {
        requireText(title, fieldName + ".title");
        requireText(description, fieldName + ".description");
        validateSpeakerEvidence(evidenceSegmentIds, speakerRole, segmentsById, fieldName);
    }

    private void validateScenarioInsights(
            AnalysisSource source,
            List<QualitativeAnalysis.ScenarioInsight> insights,
            Map<Long, AnalysisSegment> segmentsById
    ) {
        if (insights == null) {
            throw invalidResponse("scenarioInsights가 비어 있습니다.");
        }

        for (QualitativeAnalysis.ScenarioInsight insight : insights) {
            if (insight == null || insight.category() == null || insight.speakerRoles() == null) {
                throw invalidResponse("scenario insight가 올바르지 않습니다.");
            }
            if (insight.speakerRoles().isEmpty()) {
                throw invalidResponse("scenario insight에는 화자 역할이 하나 이상 필요합니다.");
            }
            if (new HashSet<>(insight.speakerRoles()).size() != insight.speakerRoles().size()) {
                throw invalidResponse("scenario insight의 화자 역할은 중복될 수 없습니다.");
            }
            if (!source.scenario().requiredRoles().containsAll(insight.speakerRoles())) {
                throw invalidResponse("scenario insight에 시나리오와 맞지 않는 화자가 포함되어 있습니다.");
            }
            if (!LinerAnalysisPolicy.scenarioInsightCategories(source.scenario())
                    .contains(insight.category())) {
                throw invalidResponse("분석 시나리오에 맞지 않는 상황별 관찰입니다.");
            }
            requireText(insight.title(), "scenarioInsight.title");
            requireText(insight.description(), "scenarioInsight.description");
            validateEvidence(insight.evidenceSegmentIds(), segmentsById, "scenarioInsight");

            for (SpeakerRole speakerRole : insight.speakerRoles()) {
                requireSpeakerEvidence(
                        insight.evidenceSegmentIds(),
                        speakerRole,
                        segmentsById,
                        "상황별 관찰"
                );
            }
        }

        validateConflictInsightStructure(source, insights);
    }

    private void validateConflictInsightStructure(
            AnalysisSource source,
            List<QualitativeAnalysis.ScenarioInsight> insights
    ) {
        if (source.scenario() != AnalysisScenario.COUPLE_CONFLICT
                && source.scenario() != AnalysisScenario.PARENT_CHILD_CONFLICT) {
            return;
        }

        List<QualitativeAnalysis.ScenarioInsight> conflictTopics = insights.stream()
                .filter(insight -> insight.category()
                        == QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_TOPIC)
                .toList();
        if (conflictTopics.size() != 1) {
            throw invalidResponse("갈등 분석에는 CONFLICT_TOPIC이 정확히 한 개 필요합니다.");
        }
        if (!Set.copyOf(conflictTopics.get(0).speakerRoles())
                .equals(source.scenario().requiredRoles())) {
            throw invalidResponse("CONFLICT_TOPIC에는 두 화자의 역할이 모두 필요합니다.");
        }

        List<QualitativeAnalysis.ScenarioInsight> positions = insights.stream()
                .filter(insight -> insight.category()
                        == QualitativeAnalysis.ScenarioInsightCategory.CONFLICT_POSITION)
                .toList();
        if (positions.stream().anyMatch(insight -> insight.speakerRoles().size() != 1)) {
            throw invalidResponse("CONFLICT_POSITION은 화자별로 분리해야 합니다.");
        }
        Set<SpeakerRole> positionRoles = positions.stream()
                .map(insight -> insight.speakerRoles().get(0))
                .collect(Collectors.toSet());
        if (positions.size() != source.scenario().requiredRoles().size()
                || !positionRoles.equals(source.scenario().requiredRoles())) {
            throw invalidResponse("CONFLICT_POSITION은 각 화자에 대해 정확히 한 개씩 필요합니다.");
        }
    }

    private <T> void validateRoles(
            AnalysisSource source,
            List<T> insights,
            Function<T, SpeakerRole> roleExtractor,
            String fieldName
    ) {
        if (insights == null) {
            throw invalidResponse(fieldName + "가 비어 있습니다.");
        }
        Set<SpeakerRole> roles = new HashSet<>();
        for (T insight : insights) {
            if (insight == null || roleExtractor.apply(insight) == null
                    || !roles.add(roleExtractor.apply(insight))) {
                throw invalidResponse(fieldName + "의 화자 역할이 올바르지 않습니다.");
            }
        }
        if (!roles.equals(source.scenario().requiredRoles())) {
            throw invalidResponse(fieldName + "의 화자 역할이 분석 시나리오와 일치하지 않습니다.");
        }
    }

    private Map<Long, AnalysisSegment> segmentsById(List<AnalysisSegment> segments) {
        Map<Long, AnalysisSegment> result = new HashMap<>();
        for (AnalysisSegment segment : segments) {
            result.put(segment.segmentId(), segment);
        }
        return result;
    }

    private void validateSpeakerEvidence(
            List<Long> evidenceSegmentIds,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById,
            String fieldName
    ) {
        validateEvidence(evidenceSegmentIds, segmentsById, fieldName);
        requireSpeakerEvidence(evidenceSegmentIds, speakerRole, segmentsById, fieldName);
    }

    private void requireSpeakerEvidence(
            List<Long> evidenceSegmentIds,
            SpeakerRole speakerRole,
            Map<Long, AnalysisSegment> segmentsById,
            String fieldName
    ) {
        boolean containsSpeakerEvidence = evidenceSegmentIds.stream()
                .map(segmentsById::get)
                .anyMatch(segment -> segment.speakerRole() == speakerRole);
        if (!containsSpeakerEvidence) {
            throw invalidResponse(fieldName + "에는 해당 화자의 근거 발화가 필요합니다.");
        }
    }

    private void validateEvidence(
            List<Long> evidenceSegmentIds,
            Map<Long, AnalysisSegment> segmentsById,
            String fieldName
    ) {
        if (evidenceSegmentIds == null || evidenceSegmentIds.isEmpty()) {
            throw invalidResponse(fieldName + "의 근거 발화는 비어 있을 수 없습니다.");
        }
        if (new HashSet<>(evidenceSegmentIds).size() != evidenceSegmentIds.size()) {
            throw invalidResponse(fieldName + "의 근거 발화는 중복될 수 없습니다.");
        }
        if (evidenceSegmentIds.stream().anyMatch(id ->
                id == null || !segmentsById.containsKey(id))) {
            throw invalidResponse(fieldName + "에 존재하지 않는 근거 발화가 포함되어 있습니다.");
        }
    }

    private void requireScore(int value, String fieldName) {
        if (value < 0 || value > 100) {
            throw invalidResponse(fieldName + "은(는) 0 이상 100 이하여야 합니다.");
        }
    }

    private void requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw invalidResponse(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }

    private void requireMaxLength(String value, int maxLength, String fieldName) {
        if (value.length() > maxLength) {
            throw invalidResponse(fieldName + "은(는) " + maxLength + "자 이하여야 합니다.");
        }
    }

    private LinerAnalysisException invalidResponse(String message) {
        return new LinerAnalysisException(
                AnalysisFailureCategory.RESPONSE_VALIDATION_ERROR,
                "invalid_analysis_response",
                message,
                null
        );
    }
}
