package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class LinerAnalysisEvaluationFixtureLoaderTest {

    private final LinerAnalysisEvaluationFixtureLoader loader =
            new LinerAnalysisEvaluationFixtureLoader(new ObjectMapper());

    @Test
    void loadsEvaluationFixturesForEveryScenario() throws Exception {
        for (AnalysisScenario scenario : AnalysisScenario.values()) {
            LinerAnalysisEvaluationFixture fixture = loader.load(scenario);

            assertThat(fixture.correctedSource().scenario()).isEqualTo(scenario);
            assertThat(fixture.noisySource().scenario()).isEqualTo(scenario);
            assertThat(fixture.evaluationSpec().scenario()).isEqualTo(scenario);
            assertThat(fixture.evaluationSpec().expectedTopics()).isNotEmpty();
            assertThat(fixture.evaluationSpec().expectedObservations()).isNotEmpty();
            assertThat(fixture.evaluationSpec().forbiddenClaims()).isNotEmpty();
            assertMatchingStructure(fixture.correctedSource(), fixture.noisySource());
        }
    }

    private void assertMatchingStructure(AnalysisSource corrected, AnalysisSource noisy) {
        assertThat(noisy.recordingId()).isEqualTo(corrected.recordingId());
        assertThat(noisy.durationMs()).isEqualTo(corrected.durationMs());
        assertThat(noisy.segments()).hasSameSizeAs(corrected.segments());

        List<AnalysisSegment> correctedSegments = corrected.segments();
        List<AnalysisSegment> noisySegments = noisy.segments();
        for (int index = 0; index < correctedSegments.size(); index++) {
            AnalysisSegment correctedSegment = correctedSegments.get(index);
            AnalysisSegment noisySegment = noisySegments.get(index);

            assertThat(noisySegment.segmentId()).isEqualTo(correctedSegment.segmentId());
            assertThat(noisySegment.speakerRole()).isEqualTo(correctedSegment.speakerRole());
            assertThat(noisySegment.startMs()).isEqualTo(correctedSegment.startMs());
            assertThat(noisySegment.endMs()).isEqualTo(correctedSegment.endMs());
        }
    }
}
