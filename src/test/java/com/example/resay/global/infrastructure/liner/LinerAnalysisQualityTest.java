package com.example.resay.global.infrastructure.liner;

import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import java.io.InputStream;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LINER_API_KEY", matches = ".+")
class LinerAnalysisQualityTest {

    @Autowired
    private LinerAnalysisModelClient modelClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void analyzesCorrectedFriendConversation() throws Exception {
        AnalysisSource source = readSource(
                "/mock/analysis/friend-daily.json"
        );

        AnalysisModelResult result = modelClient.analyze(source);

        String formattedResult = objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(objectMapper.readTree(result.resultJson()));

        System.out.println(formattedResult);

        assertThat(result.resultJson()).isNotBlank();
    }

    private AnalysisSource readSource(String path) throws Exception {
        try (InputStream inputStream = Objects.requireNonNull(
                getClass().getResourceAsStream(path)
        )) {
            return objectMapper.readValue(inputStream, AnalysisSource.class);
        }
    }
}
