package com.example.resay.domain.analysis.service;

import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.model.AnalysisModelResult;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.port.AnalysisModelClient;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.recording.dto.SpeakerMappingRequestDto;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.domain.transcription.service.SpeakerMappingService;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SpeakerMappingAnalysisPipelineIntegrationTest {

    @Autowired
    private SpeakerMappingService speakerMappingService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @MockitoBean
    private AnalysisModelClient analysisModelClient;

    private User user;
    private Recording recording;

    @AfterEach
    void tearDown() {
        if (recording != null) {
            conversationAnalysisRepository.findByRecordingId(recording.getId()).ifPresent(analysis -> {
                analysisResultRepository.findByAnalysisId(analysis.getId()).ifPresent(analysisResultRepository::delete);
                conversationAnalysisRepository.delete(analysis);
            });
            transcriptSegmentRepository.deleteAll(
                    transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId())
            );
            transcriptionRepository.findByRecordingId(recording.getId()).ifPresent(transcriptionRepository::delete);
            recordingRepository.deleteById(recording.getId());
        }
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void 화자_매핑이_완료되면_분석_결과가_저장되고_녹음이_완료된다() throws InterruptedException {
        prepareTranscribedRecording();
        AtomicReference<AnalysisSource> receivedSource = new AtomicReference<>();
        when(analysisModelClient.analyze(any())).thenAnswer(invocation -> {
            AnalysisSource source = invocation.getArgument(0);
            receivedSource.set(source);
            return successfulResult(source);
        });

        speakerMappingService.map(
                recording.getId(),
                user.getId(),
                new SpeakerMappingRequestDto("SPEAKER_1", "호석", null)
        );

        awaitPipelineCompletion();

        var analysis = conversationAnalysisRepository.findByRecordingId(recording.getId()).orElseThrow();
        var result = analysisResultRepository.findByAnalysisId(analysis.getId()).orElseThrow();
        Recording completedRecording = recordingRepository.findById(recording.getId()).orElseThrow();

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
        assertThat(result.getResultJson())
                .contains("\"recordingInfo\"")
                .contains("\"quantitativeAnalysis\"")
                .contains("\"qualitativeAnalysis\"");
        assertThat(result.getSchemaVersion()).isEqualTo("analysis-report-v5");
        assertThat(completedRecording.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        assertThat(completedRecording.getCompletedAt()).isNotNull();
        assertThat(receivedSource.get().speakers())
                .extracting(speaker -> speaker.speakerRole() + ":" + speaker.speakerName())
                .containsExactly("SELF:호준", "FRIEND:호석");
        verify(analysisModelClient, times(1)).analyze(any());
    }

    private void prepareTranscribedRecording() {
        user = userRepository.save(User.createLocal("pipeline@example.com", "encoded", "호준"));
        Recording created = Recording.create(user.getId(), "pipeline-test.m4a", 90);
        ReflectionTestUtils.setField(created, "relationshipType", RelationshipType.FRIEND_DAILY);
        ReflectionTestUtils.setField(created, "status", RecordingStatus.TRANSCRIBING);
        recording = recordingRepository.save(created);

        Transcription transcription = Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH);
        transcription.complete();
        transcriptionRepository.save(transcription);
        transcriptSegmentRepository.saveAll(List.of(
                TranscriptSegment.create(
                        recording.getId(),
                        1,
                        "1",
                        0,
                        35_000,
                        "오늘 하루 동안 있었던 일을 천천히 이야기해 볼게 생각보다 재미있는 일이 정말 많았어"
                ),
                TranscriptSegment.create(
                        recording.getId(),
                        2,
                        "2",
                        36_000,
                        71_000,
                        "좋아 어떤 일이 가장 기억에 남았는지 처음부터 자세히 들려주면 좋을 것 같아"
                )
        ));
    }

    private AnalysisModelResult successfulResult(AnalysisSource source) {
        long firstSegmentId = source.segments().get(0).segmentId();
        long secondSegmentId = source.segments().get(1).segmentId();
        return new AnalysisModelResult(
                """
                        {
                          "overview": {
                            "title": "대화 요약",
                            "description": "두 친구가 하루에 있었던 일을 나눴습니다.",
                            "evidenceSegmentIds": [%d, %d]
                          },
                          "timeline": [
                            {
                              "title": "일상 공유",
                              "description": "하루에 있었던 일을 이야기했습니다.",
                              "evidenceSegmentIds": [%d, %d]
                            }
                          ],
                          "topics": [
                            {
                              "title": "오늘 있었던 일",
                              "description": "기억에 남은 일을 이야기했습니다.",
                              "segmentIds": [%d, %d]
                            }
                          ],
                          "characterInsights": [],
                          "speakerInsights": [],
                          "interestInsights": [],
                          "spicinessInsights": [],
                          "reactionStyleInsights": [],
                          "scenarioInsights": []
                        }
                        """.formatted(
                        firstSegmentId,
                        secondSegmentId,
                        firstSegmentId,
                        secondSegmentId,
                        firstSegmentId,
                        secondSegmentId
                ),
                "fake-model",
                "test-v1",
                "test-v1"
        );
    }

    private void awaitPipelineCompletion() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            var analysis = conversationAnalysisRepository.findByRecordingId(recording.getId());
            var storedRecording = recordingRepository.findById(recording.getId());
            boolean analysisCompleted = analysis
                    .map(value -> value.getStatus() == AnalysisStatus.COMPLETED
                            && analysisResultRepository.existsByAnalysisId(value.getId()))
                    .orElse(false);
            boolean recordingCompleted = storedRecording
                    .map(value -> value.getStatus() == RecordingStatus.COMPLETED)
                    .orElse(false);
            if (analysisCompleted && recordingCompleted) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("화자 매핑 후 분석 파이프라인이 제한 시간 안에 완료되지 않았습니다.");
    }
}
