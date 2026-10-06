package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.code.AnalysisErrorCode;
import com.example.resay.domain.analysis.event.AnalysisRequestedEvent;
import com.example.resay.global.exception.GeneralException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class AnalysisRequestedEventListenerTest {

    @Mock
    private ObjectProvider<AnalysisProcessor> analysisProcessorProvider;

    @Mock
    private AnalysisProcessor analysisProcessor;

    @Test
    void startsAnalysis() {
        given(analysisProcessorProvider.getIfAvailable()).willReturn(analysisProcessor);
        AnalysisRequestedEventListener listener = listener();

        listener.handle(new AnalysisRequestedEvent(1L));

        then(analysisProcessor).should().process(1L);
    }

    @Test
    void ignoresDuplicateAnalysisRequest() {
        given(analysisProcessorProvider.getIfAvailable()).willReturn(analysisProcessor);
        willThrow(new GeneralException(AnalysisErrorCode.ANALYSIS_ALREADY_EXISTS))
                .given(analysisProcessor)
                .process(1L);
        AnalysisRequestedEventListener listener = listener();

        assertThatCode(() -> listener.handle(new AnalysisRequestedEvent(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void isolatesAnalysisFailureFromEventPublisher() {
        given(analysisProcessorProvider.getIfAvailable()).willReturn(analysisProcessor);
        willThrow(new IllegalStateException("분석 실패"))
                .given(analysisProcessor)
                .process(1L);
        AnalysisRequestedEventListener listener = listener();

        assertThatCode(() -> listener.handle(new AnalysisRequestedEvent(1L)))
                .doesNotThrowAnyException();
    }

    @Test
    void doesNothingWhenProcessorIsUnavailable() {
        given(analysisProcessorProvider.getIfAvailable()).willReturn(null);
        AnalysisRequestedEventListener listener = listener();

        assertThatCode(() -> listener.handle(new AnalysisRequestedEvent(1L)))
                .doesNotThrowAnyException();

        then(analysisProcessor).shouldHaveNoInteractions();
    }

    private AnalysisRequestedEventListener listener() {
        return new AnalysisRequestedEventListener(analysisProcessorProvider);
    }
}
