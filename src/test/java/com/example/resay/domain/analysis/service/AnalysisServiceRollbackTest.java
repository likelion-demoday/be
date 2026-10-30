package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
class AnalysisServiceRollbackTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @MockitoBean
    private AnalysisResultRepository analysisResultRepository;

    @Test
    void rollsBackCompletedStatusWhenResultSaveFails() {
        analysisService.start(9001L);
        given(analysisResultRepository.existsByAnalysisId(any())).willReturn(false);
        given(analysisResultRepository.saveAndFlush(any()))
                .willThrow(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> analysisService.complete(9001L, resultCommand()))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AnalysisErrorCode.ANALYSIS_RESULT_ALREADY_EXISTS)
                );

        assertThat(conversationAnalysisRepository.findByRecordingId(9001L))
                .get()
                .extracting(analysis -> analysis.getStatus())
                .isEqualTo(AnalysisStatus.ANALYZING);
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
