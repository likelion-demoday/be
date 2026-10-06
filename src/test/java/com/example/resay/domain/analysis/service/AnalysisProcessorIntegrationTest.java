package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.AnalysisSegment;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;

@SpringBootTest
@Import(AnalysisProcessorIntegrationTest.TestConfig.class)
@RecordApplicationEvents
class AnalysisProcessorIntegrationTest {

    @Autowired
    private AnalysisProcessor analysisProcessor;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private FakeAnalysisModelClient analysisModelClient;

    @Autowired
    private ApplicationEvents applicationEvents;

    @AfterEach
    void cleanUp() {
        analysisResultRepository.deleteAll();
        conversationAnalysisRepository.deleteAll();
        analysisModelClient.reset();
    }

    @Test
    void processesAnalysisFromSourceToStoredResult() {
        analysisProcessor.process(7001L);

        var analysis = conversationAnalysisRepository.findByRecordingId(7001L).orElseThrow();
        var result = analysisResultRepository.findByAnalysisId(analysis.getId()).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getResultJson())
                .contains("\"quantitativeAnalysis\"")
                .contains("\"qualitativeAnalysis\":{\"overview\"")
                .contains("\"title\":\"대화 요약\"");
        assertThat(result.getModelName()).isEqualTo("fake-model");
        assertThat(result.getPromptVersion()).isEqualTo("test-v1");
        assertThat(result.getSchemaVersion()).isEqualTo("analysis-report-v5");
        assertThat(analysisModelClient.wasTransactionActive()).isFalse();
    }

    @Test
    void storesFailedStatusWhenModelCallFails() {
        analysisModelClient.failNextCall();

        assertThatThrownBy(() -> analysisProcessor.process(7002L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("가짜 모델 호출 실패");

        var analysis = conversationAnalysisRepository.findByRecordingId(7002L).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
        assertThat(analysisResultRepository.findByAnalysisId(analysis.getId())).isEmpty();
        assertThat(analysisModelClient.wasTransactionActive()).isFalse();
        assertThat(applicationEvents.stream(AnalysisFailedEvent.class))
                .containsExactly(new AnalysisFailedEvent(7002L, AnalysisFailureReason.PROCESSING_ERROR));
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AnalysisSourceReader analysisSourceReader() {
            return recordingId -> new AnalysisSource(
                    recordingId,
                    AnalysisScenario.COUPLE_DAILY,
                    90_000L,
                    speakersFor(AnalysisScenario.COUPLE_DAILY),
                    List.of(
                            new AnalysisSegment(
                                    1L,
                                    SpeakerRole.SELF,
                                    0L,
                                    35_000L,
                                    "오늘 하루 동안 있었던 일을 천천히 이야기해 볼게 생각보다 재미있는 일이 정말 많았어"
                            ),
                            new AnalysisSegment(
                                    2L,
                                    SpeakerRole.PARTNER,
                                    36_000L,
                                    71_000L,
                                    "좋아 어떤 일이 가장 기억에 남았는지 처음부터 자세히 들려주면 좋을 것 같아"
                            )
                    )
            );
        }

        @Bean
        FakeAnalysisModelClient analysisModelClient() {
            return new FakeAnalysisModelClient();
        }

    }

    static class FakeAnalysisModelClient implements AnalysisModelClient {

        private boolean failNextCall;
        private boolean transactionActive;

        @Override
        public AnalysisModelResult analyze(AnalysisSource source) {
            transactionActive = TransactionSynchronizationManager.isActualTransactionActive();
            if (failNextCall) {
                throw new RuntimeException("가짜 모델 호출 실패");
            }
            return new AnalysisModelResult(
                    """
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
                            """,
                    "fake-model",
                    "test-v1",
                    "test-v1"
            );
        }

        void failNextCall() {
            this.failNextCall = true;
        }

        boolean wasTransactionActive() {
            return transactionActive;
        }

        void reset() {
            failNextCall = false;
            transactionActive = false;
        }
    }
}
