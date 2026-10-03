package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LinerAnalysisResponseGrounder {

    public QualitativeAnalysis ground(
            AnalysisSource source,
            QualitativeAnalysis response
    ) {
        if (source == null || response == null || response.speakerInsights() == null) {
            return response;
        }

        List<QualitativeAnalysis.SpeakerInsight> groundedSpeakerInsights =
                response.speakerInsights().stream()
                        .map(insight -> groundSpeakerInsight(source, insight))
                        .toList();

        return new QualitativeAnalysis(
                response.overview(),
                response.timeline(),
                groundTopics(response.topics()),
                response.characterInsights(),
                groundedSpeakerInsights,
                response.interestInsights(),
                response.spicinessInsights(),
                response.reactionStyleInsights(),
                response.scenarioInsights()
        );
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
                insight.sentenceStyle(),
                groundedExpressions
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
                expression.expression(),
                occurrenceCount,
                expression.description(),
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
}
