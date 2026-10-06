package com.example.resay.domain.analysis.config;

import com.example.resay.domain.analysis.service.AnalysisProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AnalysisProcessorConfigIntegrationTest {

    @Autowired
    private AnalysisProcessor analysisProcessor;

    @Test
    void registersAnalysisProcessorOutsideMockProfile() {
        assertThat(analysisProcessor).isNotNull();
    }
}
