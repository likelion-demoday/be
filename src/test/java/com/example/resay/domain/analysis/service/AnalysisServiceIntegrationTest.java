package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
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
    private EntityManager entityManager;

    @Test
    void persistsCompletedStatus() {
        analysisService.start(1L);

        analysisService.complete(1L);
        entityManager.flush();
        entityManager.clear();

        assertThat(conversationAnalysisRepository.findByRecordingId(1L))
                .get()
                .extracting(analysis -> analysis.getStatus())
                .isEqualTo(AnalysisStatus.COMPLETED);
    }
}
