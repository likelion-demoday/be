package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AnalysisProcessorTest {

    @Mock
    private AnalysisService analysisService;

    @Mock
    private AnalysisSourceReader analysisSourceReader;

    @Mock
    private AnalysisModelClient analysisModelClient;

    private AnalysisProcessor analysisProcessor;

    @BeforeEach
    void setUp() {
        analysisProcessor = new AnalysisProcessor(
                analysisService,
                analysisSourceReader,
                analysisModelClient,
                new ConversationMetricsCalculator(),
                new AnalysisReportAssembler(new ObjectMapper()),
                Runnable::run
        );
    }

    @Test
    void processesAnalysis() {
        AnalysisSource source = analysisSource(1L);
        given(analysisSourceReader.read(1L)).willReturn(source);
        given(analysisModelClient.analyze(source)).willReturn(modelResult());

        analysisProcessor.process(1L);

        then(analysisService).should().start(1L);
        then(analysisModelClient).should().analyze(source);
        ArgumentCaptor<AnalysisResultCommand> captor = ArgumentCaptor.forClass(AnalysisResultCommand.class);
        then(analysisService).should().complete(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().resultJson())
                .contains("\"recordingInfo\"")
                .contains("\"quantitativeAnalysis\"")
                .contains("\"qualitativeAnalysis\":{\"overview\"")
                .contains("\"title\":\"대화 요약\"");
        assertThat(captor.getValue().modelName()).isEqualTo("liner-mark-1.1");
        then(analysisService).should(never()).fail(1L);
    }

    @Test
    void marksAnalysisFailedWhenModelCallFails() {
        AnalysisSource source = analysisSource(1L);
        RuntimeException modelException = new RuntimeException("모델 호출 실패");
        given(analysisSourceReader.read(1L)).willReturn(source);
        given(analysisModelClient.analyze(source)).willThrow(modelException);

        assertThatThrownBy(() -> analysisProcessor.process(1L))
                .isSameAs(modelException);

        then(analysisService).should().fail(1L);
        then(analysisService).should(never()).complete(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void doesNotFailExistingAnalysisWhenStartFails() {
        RuntimeException startException = new RuntimeException("분석 시작 실패");
        willThrow(startException).given(analysisService).start(1L);

        assertThatThrownBy(() -> analysisProcessor.process(1L))
                .isSameAs(startException);

        then(analysisSourceReader).shouldHaveNoInteractions();
        then(analysisModelClient).shouldHaveNoInteractions();
        then(analysisService).should(never()).fail(1L);
    }

    @Test
    void rejectsMismatchedRecordingSource() {
        given(analysisSourceReader.read(1L)).willReturn(analysisSource(2L));

        assertThatThrownBy(() -> analysisProcessor.process(1L))
                .isInstanceOf(IllegalStateException.class);

        then(analysisModelClient).shouldHaveNoInteractions();
        then(analysisService).should().fail(1L);
    }

    @Test
    void preservesOriginalExceptionWhenFailStatusUpdateAlsoFails() {
        RuntimeException modelException = new RuntimeException("모델 호출 실패");
        RuntimeException statusException = new RuntimeException("실패 상태 저장 실패");
        AnalysisSource source = analysisSource(1L);
        given(analysisSourceReader.read(1L)).willReturn(source);
        given(analysisModelClient.analyze(source)).willThrow(modelException);
        willThrow(statusException).given(analysisService).fail(1L);

        assertThatThrownBy(() -> analysisProcessor.process(1L))
                .isSameAs(modelException)
                .satisfies(exception ->
                        assertThat(exception.getSuppressed()).containsExactly(statusException)
                );
    }

    private AnalysisSource analysisSource(Long recordingId) {
        return new AnalysisSource(
                recordingId,
                AnalysisScenario.COUPLE_DAILY,
                10_000L,
                List.of(
                        new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                        new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
                )
        );
    }

    private AnalysisModelResult modelResult() {
        return new AnalysisModelResult(
                qualitativeResultJson(),
                "liner-mark-1.1",
                "v1",
                "v1"
        );
    }

    private String qualitativeResultJson() {
        return """
                {
                  "overview": {
                    "title": "대화 요약",
                    "description": "두 사람이 인사를 나눴습니다.",
                    "evidenceSegmentIds": [1, 2]
                  },
                  "timeline": [
                    {
                      "title": "인사",
                      "description": "두 사람이 서로 인사했습니다.",
                      "evidenceSegmentIds": [1, 2]
                    }
                  ],
                  "topics": [
                    {
                      "title": "인사",
                      "description": "두 사람이 서로 인사했습니다.",
                      "segmentIds": [1, 2]
                    }
                  ],
                  "characterInsights": [],
                  "speakerInsights": [],
                  "interestInsights": [],
                  "spicinessInsights": [],
                  "reactionStyleInsights": [],
                  "scenarioInsights": []
                }
                """;
    }
}
