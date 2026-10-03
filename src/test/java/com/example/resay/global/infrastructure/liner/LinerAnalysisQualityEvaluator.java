package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

class LinerAnalysisQualityEvaluator {

    LinerAnalysisQualityEvaluation evaluate(
            LinerAnalysisEvaluationSpec spec,
            QualitativeAnalysis analysis,
            LinerAnalysisQualityEvaluation.TranscriptVariant transcriptVariant
    ) {
        List<ActualObservation> actualObservations = actualObservations(analysis);
        List<LinerAnalysisQualityEvaluation.ObservationEvaluation> observations =
                spec.expectedObservations().stream()
                        .map(expected -> evaluateObservation(expected, actualObservations))
                        .toList();
        Set<Long> referencedEvidence = referencedEvidence(analysis);
        boolean noisyTranscript = transcriptVariant
                == LinerAnalysisQualityEvaluation.TranscriptVariant.NOISY;

        return new LinerAnalysisQualityEvaluation(
                spec.scenario(),
                transcriptVariant,
                spec.expectedTopics(),
                analysis.timeline(),
                observations,
                detectedForbiddenClaims(spec.forbiddenClaims(), analysis),
                noisyTranscript
                        ? intersection(referencedEvidence, spec.notableCorruptedSegmentIds())
                        : List.of(),
                noisyTranscript
                        ? intersection(referencedEvidence, spec.meaningRiskSegmentIds())
                        : List.of()
        );
    }

    private LinerAnalysisQualityEvaluation.ObservationEvaluation evaluateObservation(
            LinerAnalysisEvaluationSpec.ExpectedObservation expected,
            List<ActualObservation> actualObservations
    ) {
        List<ActualObservation> matched = actualObservations.stream()
                .filter(actual -> actual.category().equals(expected.category()))
                .filter(actual -> matchesSpeakerRole(expected, actual))
                .toList();
        List<Long> actualEvidence = matched.stream()
                .flatMap(actual -> actual.evidenceSegmentIds().stream())
                .distinct()
                .sorted()
                .toList();

        return new LinerAnalysisQualityEvaluation.ObservationEvaluation(
                expected.category(),
                expected.speakerRole(),
                !matched.isEmpty(),
                expected.evidenceSegmentIds(),
                actualEvidence,
                intersection(actualEvidence, expected.evidenceSegmentIds())
        );
    }

    private boolean matchesSpeakerRole(
            LinerAnalysisEvaluationSpec.ExpectedObservation expected,
            ActualObservation actual
    ) {
        if (expected.speakerRole() == null) {
            return true;
        }
        if ("CONFLICT_POSITION".equals(expected.category())) {
            return actual.speakerRoles().equals(Set.of(expected.speakerRole()));
        }
        return actual.speakerRoles().contains(expected.speakerRole());
    }

    private List<ActualObservation> actualObservations(QualitativeAnalysis analysis) {
        List<ActualObservation> result = new ArrayList<>();

        for (QualitativeAnalysis.SpeakerInsight insight : analysis.speakerInsights()) {
            for (QualitativeAnalysis.SpeakerPattern pattern : insight.patterns()) {
                result.add(new ActualObservation(
                        pattern.category().name(),
                        Set.of(insight.speakerRole()),
                        pattern.evidenceSegmentIds()
                ));
            }
        }

        for (QualitativeAnalysis.ScenarioInsight insight : analysis.scenarioInsights()) {
            result.add(new ActualObservation(
                    insight.category().name(),
                    Set.copyOf(insight.speakerRoles()),
                    insight.evidenceSegmentIds()
            ));
        }

        for (QualitativeAnalysis.InterestInsight insight : analysis.interestInsights()) {
            for (QualitativeAnalysis.InterestObservation observation : insight.observations()) {
                result.add(new ActualObservation(
                        observation.category().name(),
                        Set.of(insight.speakerRole()),
                        observation.evidenceSegmentIds()
                ));
            }
        }

        for (QualitativeAnalysis.SpicinessInsight insight : analysis.spicinessInsights()) {
            for (QualitativeAnalysis.SpicinessObservation observation : insight.observations()) {
                result.add(new ActualObservation(
                        observation.category().name(),
                        Set.of(insight.speakerRole()),
                        observation.evidenceSegmentIds()
                ));
            }
        }

        for (QualitativeAnalysis.ReactionStyleInsight insight : analysis.reactionStyleInsights()) {
            for (QualitativeAnalysis.ReactionExample example : insight.examples()) {
                result.add(new ActualObservation(
                        example.category().name(),
                        Set.of(insight.speakerRole()),
                        example.evidenceSegmentIds()
                ));
            }
        }

        return List.copyOf(result);
    }

    private List<String> detectedForbiddenClaims(
            List<String> forbiddenClaims,
            QualitativeAnalysis analysis
    ) {
        String analysisText = normalize(allAnalysisText(analysis));
        return forbiddenClaims.stream()
                .filter(claim -> analysisText.contains(normalize(claim)))
                .toList();
    }

    private String allAnalysisText(QualitativeAnalysis analysis) {
        List<String> values = new ArrayList<>();
        values.add(analysis.overview().title());
        values.add(analysis.overview().description());

        for (QualitativeAnalysis.TimelineItem item : analysis.timeline()) {
            values.add(item.title());
            values.add(item.description());
        }
        for (QualitativeAnalysis.Topic topic : analysis.topics()) {
            values.add(topic.title());
            values.add(topic.description());
        }
        for (QualitativeAnalysis.CharacterInsight insight : analysis.characterInsights()) {
            values.add(insight.name());
            values.add(insight.description());
        }
        for (QualitativeAnalysis.SpeakerInsight insight : analysis.speakerInsights()) {
            for (QualitativeAnalysis.SpeakerPattern pattern : insight.patterns()) {
                values.add(pattern.title());
                values.add(pattern.description());
            }
            values.add(insight.sentenceStyle().title());
            values.add(insight.sentenceStyle().description());
            for (QualitativeAnalysis.FrequentExpression expression :
                    insight.frequentExpressions()) {
                values.add(expression.expression());
                values.add(expression.description());
            }
        }
        for (QualitativeAnalysis.InterestInsight insight : analysis.interestInsights()) {
            values.add(insight.description());
            for (QualitativeAnalysis.InterestObservation observation : insight.observations()) {
                values.add(observation.title());
                values.add(observation.description());
            }
        }
        for (QualitativeAnalysis.SpicinessInsight insight : analysis.spicinessInsights()) {
            values.add(insight.description());
            for (QualitativeAnalysis.SpicinessObservation observation : insight.observations()) {
                values.add(observation.title());
                values.add(observation.description());
            }
        }
        for (QualitativeAnalysis.ReactionStyleInsight insight :
                analysis.reactionStyleInsights()) {
            values.add(insight.description());
            for (QualitativeAnalysis.ReactionExample example : insight.examples()) {
                values.add(example.title());
                values.add(example.description());
            }
        }
        for (QualitativeAnalysis.ScenarioInsight insight : analysis.scenarioInsights()) {
            values.add(insight.title());
            values.add(insight.description());
        }

        return String.join(" ", values);
    }

    private Set<Long> referencedEvidence(QualitativeAnalysis analysis) {
        Set<Long> result = new HashSet<>(analysis.overview().evidenceSegmentIds());

        analysis.timeline().forEach(item -> result.addAll(item.evidenceSegmentIds()));
        analysis.topics().forEach(item -> result.addAll(item.segmentIds()));
        analysis.characterInsights().forEach(item ->
                result.addAll(item.evidenceSegmentIds()));
        analysis.speakerInsights().forEach(insight -> insight.patterns()
                .forEach(pattern -> result.addAll(pattern.evidenceSegmentIds())));
        analysis.speakerInsights().forEach(insight -> {
            result.addAll(insight.sentenceStyle().evidenceSegmentIds());
            insight.frequentExpressions().forEach(expression ->
                    result.addAll(expression.evidenceSegmentIds()));
        });
        analysis.interestInsights().forEach(insight -> insight.observations()
                .forEach(observation -> result.addAll(observation.evidenceSegmentIds())));
        analysis.spicinessInsights().forEach(insight -> insight.observations()
                .forEach(observation -> result.addAll(observation.evidenceSegmentIds())));
        analysis.reactionStyleInsights().forEach(insight -> insight.examples()
                .forEach(example -> result.addAll(example.evidenceSegmentIds())));
        analysis.scenarioInsights().forEach(insight ->
                result.addAll(insight.evidenceSegmentIds()));

        return result;
    }

    private List<Long> intersection(Collection<Long> left, Collection<Long> right) {
        Set<Long> rightValues = new HashSet<>(right);
        return left.stream()
                .filter(rightValues::contains)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private record ActualObservation(
            String category,
            Set<SpeakerRole> speakerRoles,
            List<Long> evidenceSegmentIds
    ) {
    }
}
