package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.config.AnalysisStallProperties;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AnalysisStallReconcilerTest {

    @Mock
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Mock
    private AnalysisStallService analysisStallService;

    @Test
    void usesConfiguredTimeoutAndProcessesBothStallShapes() {
        ConversationAnalysis analysis = ConversationAnalysis.start(20L);
        ReflectionTestUtils.setField(analysis, "id", 10L);
        given(conversationAnalysisRepository.findStalledAnalyzing(any()))
                .willReturn(List.of(analysis));
        given(conversationAnalysisRepository
                .findStalledAnalyzingRecordingIdsWithoutAnalysis(any()))
                .willReturn(List.of(30L));
        given(analysisStallService.failStalledAnalysis(
                any(), any(), any(), any()
        )).willReturn(true);
        given(analysisStallService.failStalledRecordingWithoutAnalysis(any(), any()))
                .willReturn(true);
        AnalysisStallReconciler reconciler = new AnalysisStallReconciler(
                conversationAnalysisRepository,
                analysisStallService,
                new AnalysisStallProperties(Duration.ofMinutes(60))
        );
        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        LocalDateTime earliest = LocalDateTime.now().minusMinutes(60);

        reconciler.reconcile();

        then(conversationAnalysisRepository).should().findStalledAnalyzing(cutoff.capture());
        assertThat(cutoff.getValue())
                .isAfterOrEqualTo(earliest)
                .isBeforeOrEqualTo(LocalDateTime.now().minusMinutes(60));
        then(analysisStallService).should().failStalledAnalysis(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(20L),
                org.mockito.ArgumentMatchers.eq(cutoff.getValue()),
                any()
        );
        then(analysisStallService).should().failStalledRecordingWithoutAnalysis(
                30L,
                cutoff.getValue()
        );
    }
}
