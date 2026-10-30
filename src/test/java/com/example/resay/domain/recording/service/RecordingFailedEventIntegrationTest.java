package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import com.example.resay.domain.recording.repository.RecordingRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

// 크레딧 환급 쪽처럼 실패가 커밋된 뒤에 이벤트를 받는 리스너 입장에서 확인한다
@SpringBootTest
class RecordingFailedEventIntegrationTest {

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReceivedEvents receivedEvents;

    private Recording recording;

    @BeforeEach
    void setUp() {
        receivedEvents.events.clear();
        recording = Recording.create(1L, "failed-event-test.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recordingRepository.save(recording);
    }

    @AfterEach
    void tearDown() {
        recordingRepository.deleteById(recording.getId());
    }

    @Test
    void 실패가_커밋되면_사유와_함께_이벤트가_전달된다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                recordingService.failTranscription(recording.getId(), RecordingFailureReason.TRANSCRIPTION_TIMEOUT));

        assertThat(receivedEvents.events).containsExactly(
                new RecordingFailedEvent(recording.getId(), RecordingFailureReason.TRANSCRIPTION_TIMEOUT));
    }

    @Test
    void 실패가_롤백되면_이벤트가_전달되지_않는다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            recordingService.failTranscription(recording.getId(), RecordingFailureReason.TRANSCRIPTION_TIMEOUT);
            status.setRollbackOnly();
        });

        assertThat(receivedEvents.events).isEmpty();
        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getStatus())
                .isEqualTo(RecordingStatus.TRANSCRIBING);
    }

    @TestConfiguration
    static class ListenerConfig {

        @Bean
        ReceivedEvents receivedEvents() {
            return new ReceivedEvents();
        }
    }

    static class ReceivedEvents {

        final List<RecordingFailedEvent> events = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void on(RecordingFailedEvent event) {
            events.add(event);
        }
    }
}
