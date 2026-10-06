package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.dto.AnalysisReportDto;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.ConversationMetrics;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.service.AnalysisReportAssembler;
import com.example.resay.domain.analysis.service.ConversationMetricsCalculator;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LINER_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "LINER_PROFANITY_QUALITY_TEST", matches = "true")
class LinerProfanityQualityTest {

    @Autowired
    private LinerAnalysisModelClient modelClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ConversationMetricsCalculator metricsCalculator;

    @Autowired
    private AnalysisReportAssembler reportAssembler;

    @Test
    void detectsRepeatedProfanityInFriendlyDailyConversation() throws Exception {
        AnalysisSource source = loadSource();
        AnalysisModelResult result = modelClient.analyze(source);
        QualitativeAnalysis analysis = objectMapper.readValue(
                result.resultJson(),
                QualitativeAnalysis.class
        );
        ConversationMetrics metrics = metricsCalculator.calculate(source);
        AnalysisModelResult reportResult = reportAssembler.assemble(source, metrics, result);
        AnalysisReport report = objectMapper.readValue(
                reportResult.resultJson(),
                AnalysisReport.class
        );

        Path outputDirectory = Path.of("build", "reports", "liner-quality", "manual");
        Files.createDirectories(outputDirectory);
        Path analysisOutput = outputDirectory.resolve("friend-daily-profanity-analysis.json");
        Path frontendOutput = outputDirectory.resolve("friend-daily-profanity-response.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(analysisOutput.toFile(), analysis);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(
                frontendOutput.toFile(),
                AnalysisReportDto.from(report)
        );

        assertProfanityCounts(analysis, SpeakerRole.SELF, Map.of(
                "존나", 5,
                "시발", 1,
                "씨발", 1
        ));
        assertProfanityCounts(analysis, SpeakerRole.FRIEND, Map.of(
                "존나", 4,
                "시발", 1,
                "씨발", 1,
                "시바", 1,
                "씨바", 1
        ));

        System.out.println(analysisOutput.toAbsolutePath());
        System.out.println(frontendOutput.toAbsolutePath());
    }

    private AnalysisSource loadSource() throws Exception {
        try (InputStream inputStream = Objects.requireNonNull(
                getClass().getResourceAsStream(
                        "/analysis/manual/friend-daily-profanity.json"
                )
        )) {
            return objectMapper.readValue(inputStream, AnalysisSource.class);
        }
    }

    private void assertProfanityCounts(
            QualitativeAnalysis analysis,
            SpeakerRole speakerRole,
            Map<String, Integer> expectedCounts
    ) {
        QualitativeAnalysis.SpicinessInsight insight = analysis.spicinessInsights().stream()
                .filter(candidate -> candidate.speakerRole() == speakerRole)
                .findFirst()
                .orElseThrow();
        Map<String, Integer> actualCounts = insight.swearWords().stream()
                .collect(Collectors.toMap(
                        QualitativeAnalysis.SwearWordUsage::expression,
                        QualitativeAnalysis.SwearWordUsage::count,
                        Math::max
                ));

        assertThat(actualCounts).containsAllEntriesOf(expectedCounts);
    }
}
