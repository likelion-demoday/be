package com.example.resay.domain.analysis.repository;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
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
class ConversationAnalysisRepositoryTest {

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Test
    void savesAnalysisWithAuditingTimestamps() {
        ConversationAnalysis saved = conversationAnalysisRepository.saveAndFlush(
                ConversationAnalysis.start(1L)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(AnalysisStatus.ANALYZING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void findsAnalysisByRecordingId() {
        conversationAnalysisRepository.saveAndFlush(ConversationAnalysis.start(1L));

        assertThat(conversationAnalysisRepository.findByRecordingId(1L)).isPresent();
        assertThat(conversationAnalysisRepository.existsByRecordingId(1L)).isTrue();
        assertThat(conversationAnalysisRepository.existsByRecordingId(2L)).isFalse();
    }

    @Test
    void rejectsDuplicateRecordingId() {
        conversationAnalysisRepository.saveAndFlush(ConversationAnalysis.start(1L));

        assertThatThrownBy(() -> conversationAnalysisRepository.saveAndFlush(
                ConversationAnalysis.start(1L)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }
}
