package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.service.AnalysisReportAssembler;
import com.example.resay.domain.analysis.service.ConversationMetricsCalculator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LINER_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(
        named = "LINER_QUALITY_SCENARIO",
        matches = "FRIEND_DAILY|COUPLE_DAILY|COUPLE_CONFLICT|PARENT_CHILD_CONFLICT"
)
@EnabledIfEnvironmentVariable(
        named = "LINER_QUALITY_VARIANT",
        matches = "CORRECTED|NOISY"
)
class LinerAnalysisQualityTest {

    @Autowired
    private LinerAnalysisModelClient modelClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMetricsCalculator metricsCalculator;

    @Autowired
    private AnalysisReportAssembler reportAssembler;

    @Test
    void analyzesSelectedConversationAndPrintsQualityEvaluation() throws Exception {
        AnalysisScenario scenario = AnalysisScenario.valueOf(
                System.getenv("LINER_QUALITY_SCENARIO")
        );
        LinerAnalysisQualityEvaluation.TranscriptVariant transcriptVariant =
                LinerAnalysisQualityEvaluation.TranscriptVariant.valueOf(
                        System.getenv("LINER_QUALITY_VARIANT")
                );
        LinerAnalysisEvaluationFixture fixture =
                new LinerAnalysisEvaluationFixtureLoader(objectMapper).load(scenario);
        AnalysisSource source = transcriptVariant
                == LinerAnalysisQualityEvaluation.TranscriptVariant.CORRECTED
                ? fixture.correctedSource()
                : fixture.noisySource();

        AnalysisModelResult result = modelClient.analyze(source);
        QualitativeAnalysis analysis = objectMapper.readValue(
                result.resultJson(),
                QualitativeAnalysis.class
        );
        LinerAnalysisQualityEvaluation evaluation =
                new LinerAnalysisQualityEvaluator().evaluate(
                        fixture.evaluationSpec(),
                        analysis,
                        transcriptVariant
                );
        ConversationMetrics metrics = metricsCalculator.calculate(source);
        AnalysisModelResult reportResult = reportAssembler.assemble(source, metrics, result);
        AnalysisReport report = objectMapper.readValue(
                reportResult.resultJson(),
                AnalysisReport.class
        );

        QualityTestOutput output = new QualityTestOutput(evaluation, report);
        Path outputDirectory = Path.of("build", "reports", "liner-quality");
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve(
                scenario.name().toLowerCase() + "-"
                        + transcriptVariant.name().toLowerCase() + ".json"
        );
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), output);

        System.out.println(outputPath.toAbsolutePath());

        assertThat(evaluation.scenario()).isEqualTo(scenario);
        assertThat(report.recordingInfo().scenario()).isEqualTo(scenario);
        assertThat(report.qualitativeAnalysis().timeline()).isNotEmpty();
    }

    private record QualityTestOutput(
            LinerAnalysisQualityEvaluation evaluation,
            AnalysisReport report
    ) {
    }
}
