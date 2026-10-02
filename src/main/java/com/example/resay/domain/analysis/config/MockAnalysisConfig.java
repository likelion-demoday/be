package com.example.resay.domain.analysis.config;

import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import com.example.resay.domain.analysis.service.AnalysisProcessor;
import com.example.resay.domain.analysis.service.AnalysisReportAssembler;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.analysis.service.ConversationMetricsCalculator;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile({"local", "mock"})
@Configuration
public class MockAnalysisConfig {

    @Bean
    public AnalysisProcessor analysisProcessor(
            AnalysisService analysisService,
            AnalysisSourceReader analysisSourceReader,
            AnalysisModelClient analysisModelClient,
            ConversationMetricsCalculator metricsCalculator,
            AnalysisReportAssembler reportAssembler,
            @Qualifier("analysisTaskExecutor") Executor analysisTaskExecutor
    ) {
        return new AnalysisProcessor(
                analysisService,
                analysisSourceReader,
                analysisModelClient,
                metricsCalculator,
                reportAssembler,
                analysisTaskExecutor
        );
    }
}
