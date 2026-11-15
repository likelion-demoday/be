package com.example.resay.domain.recording.service;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.event.RecordingFailedEvent;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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

    @TempDir
    Path tempDir;

    @Autowired
    private RecordingService recordingService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReceivedEvents receivedEvents;

    private Recording recording;
    private Path audioPath;

    @BeforeEach
    void setUp() throws Exception {
        receivedEvents.events.clear();
        audioPath = Files.writeString(tempDir.resolve("failed-event-test.m4a"), "audio");
        recording = Recording.create(1L, audioPath.toString(), 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recordingRepository.save(recording);
        transcriptSegmentRepository.save(TranscriptSegment.create(
                recording.getId(), 1, "1", 0, 1000, "실패 전에 저장된 전사"
        ));
    }

    @AfterEach
    void tearDown() {
        transcriptSegmentRepository.deleteAll(
                transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId())
        );
        recordingRepository.deleteById(recording.getId());
    }

    @Test
    void 실패가_커밋되면_사유와_함께_이벤트가_전달된다() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                recordingService.failTranscription(recording.getId(), RecordingFailureReason.TRANSCRIPTION_TIMEOUT));

        assertThat(receivedEvents.events).containsExactly(
                new RecordingFailedEvent(recording.getId(), RecordingFailureReason.TRANSCRIPTION_TIMEOUT));
        Recording failed = recordingRepository.findById(recording.getId()).orElseThrow();
        assertThat(failed.hasAudio()).isFalse();
        assertThat(Files.exists(audioPath)).isFalse();
        assertThat(transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId()))
                .isEmpty();
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
        assertThat(Files.exists(audioPath)).isTrue();
        assertThat(transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId()))
                .hasSize(1);
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
