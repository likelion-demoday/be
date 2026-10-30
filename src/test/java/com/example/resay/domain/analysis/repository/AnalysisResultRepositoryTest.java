package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.AnalysisResult;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.global.config.JpaAuditingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class AnalysisResultRepositoryTest {

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Test
    void savesAnalysisResultWithAuditingTimestamps() {
        ConversationAnalysis analysis = conversationAnalysisRepository.saveAndFlush(
                ConversationAnalysis.start(1L)
        );

        AnalysisResult saved = analysisResultRepository.saveAndFlush(createResult(analysis.getId()));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsAnalysisResultByAnalysisId() {
        ConversationAnalysis analysis = conversationAnalysisRepository.saveAndFlush(
                ConversationAnalysis.start(1L)
        );
        analysisResultRepository.saveAndFlush(createResult(analysis.getId()));

        assertThat(analysisResultRepository.findByAnalysisId(analysis.getId())).isPresent();
        assertThat(analysisResultRepository.existsByAnalysisId(analysis.getId())).isTrue();
    }

    @Test
    void rejectsDuplicateAnalysisResult() {
        ConversationAnalysis analysis = conversationAnalysisRepository.saveAndFlush(
                ConversationAnalysis.start(1L)
        );
        analysisResultRepository.saveAndFlush(createResult(analysis.getId()));

        assertThatThrownBy(() -> analysisResultRepository.saveAndFlush(createResult(analysis.getId())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private AnalysisResult createResult(Long analysisId) {
        return AnalysisResult.create(
                analysisId,
                "{\"summary\":\"대화 요약\"}",
                "liner-mark-1.1",
                "v1",
                "v1"
        );
    }
}
