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
                .filter(actual -> expected.speakerRole() == null
                        || actual.speakerRoles().contains(expected.speakerRole()))
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
        for (QualitativeAnalysis.SpeakerInsight insight : analysis.speakerInsights()) {
            for (QualitativeAnalysis.SpeakerPattern pattern : insight.patterns()) {
                values.add(pattern.title());
                values.add(pattern.description());
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
        analysis.speakerInsights().forEach(insight -> insight.patterns()
                .forEach(pattern -> result.addAll(pattern.evidenceSegmentIds())));
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
