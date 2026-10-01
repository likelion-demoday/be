package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.global.config.JpaAuditingConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({JpaAuditingConfig.class, AnalysisService.class})
class AnalysisServiceIntegrationTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsResultAndCompletedStatus() {
        analysisService.start(1L);

        analysisService.complete(1L, resultCommand());
        entityManager.flush();
        entityManager.clear();

        var analysis = conversationAnalysisRepository.findByRecordingId(1L).orElseThrow();
        var result = analysisResultRepository.findByAnalysisId(analysis.getId()).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getResultJson()).isEqualTo("{\"summary\":\"대화 요약\"}");
        assertThat(result.getModelName()).isEqualTo("liner-mark-1.1");
        assertThat(result.getPromptVersion()).isEqualTo("v1");
        assertThat(result.getSchemaVersion()).isEqualTo("v1");
    }

    private AnalysisResultCommand resultCommand() {
        return new AnalysisResultCommand(
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        );
    }
}
