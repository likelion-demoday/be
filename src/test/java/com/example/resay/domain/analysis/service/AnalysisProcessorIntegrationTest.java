package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(AnalysisProcessorIntegrationTest.TestConfig.class)
class AnalysisProcessorIntegrationTest {

    @Autowired
    private AnalysisProcessor analysisProcessor;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private FakeAnalysisModelClient analysisModelClient;

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
        assertThat(result.getResultJson()).isEqualTo("{\"summary\":\"대화 요약\"}");
        assertThat(result.getModelName()).isEqualTo("fake-model");
        assertThat(result.getPromptVersion()).isEqualTo("test-v1");
        assertThat(result.getSchemaVersion()).isEqualTo("test-v1");
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
        assertThat(analysisResultRepository.findByAnalysisId(analysis.getId())).isEmpty();
        assertThat(analysisModelClient.wasTransactionActive()).isFalse();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AnalysisSourceReader analysisSourceReader() {
            return recordingId -> new AnalysisSource(
                    recordingId,
                    AnalysisScenario.COUPLE_DAILY,
                    10_000L,
                    List.of(
                            new AnalysisSegment(1L, SpeakerRole.SELF, 100L, 500L, "안녕"),
                            new AnalysisSegment(2L, SpeakerRole.PARTNER, 600L, 900L, "반가워")
                    )
            );
        }

        @Bean
        FakeAnalysisModelClient analysisModelClient() {
            return new FakeAnalysisModelClient();
        }

        @Bean
        AnalysisProcessor analysisProcessor(
                AnalysisService analysisService,
                AnalysisSourceReader analysisSourceReader,
                AnalysisModelClient analysisModelClient
        ) {
            return new AnalysisProcessor(
                    analysisService,
                    analysisSourceReader,
                    analysisModelClient
            );
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
                    "{\"summary\":\"대화 요약\"}",
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
