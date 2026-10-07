package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.AnalysisStatus;
import com.example.resay.domain.analysis.event.AnalysisCompletedEvent;
import com.example.resay.domain.analysis.event.AnalysisFailedEvent;
import com.example.resay.domain.analysis.event.AnalysisRequestedEvent;
import com.example.resay.domain.analysis.model.AnalysisSource;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.port.AnalysisSourceReader;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.analysis.service.AnalysisProcessor;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 화자 매핑 저장 → 분석 요청 이벤트 → 분석 입력 조립 → 분석 완료 반영까지 실제 DB로 확인한다
// (분석 요청 이벤트를 받는 쪽은 커밋 후 실행되므로 트랜잭션을 걸지 않고, 끝나면 직접 정리한다)
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
class SpeakerMappingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

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
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AnalysisSourceReader analysisSourceReader;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationEvents applicationEvents;

    // 실제 분석(LINER 호출)은 이 테스트의 범위가 아니다
    @MockitoBean
    private AnalysisProcessor analysisProcessor;

    private User user;
    private Recording recording;

    // 이 테스트가 만든 데이터만 지운다
    @AfterEach
    void tearDown() {
        if (recording != null) {
            conversationAnalysisRepository.findByRecordingId(recording.getId())
                    .ifPresent(conversationAnalysisRepository::delete);
            transcriptSegmentRepository.deleteAll(
                    transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId()));
            transcriptionRepository.findByRecordingId(recording.getId()).ifPresent(transcriptionRepository::delete);
            recordingRepository.deleteById(recording.getId());
        }
        if (user != null) {
            userRepository.deleteById(user.getId());
        }
    }

    @Test
    void 화자를_지정하면_분석을_요청하고_분석_입력이_합의한_구조로_만들어진다() throws Exception {
        transcribed(RelationshipType.FRIEND_DAILY);

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_2","partnerNickname":"호석"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_2"));

        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getStatus())
                .isEqualTo(RecordingStatus.ANALYZING);
        assertThat(applicationEvents.stream(AnalysisRequestedEvent.class))
                .containsExactly(new AnalysisRequestedEvent(recording.getId()));

        AnalysisSource source = analysisSourceReader.read(recording.getId());
        assertThat(source.durationMs()).isEqualTo(600_000L);
        assertThat(source.speakers()).extracting(speaker -> speaker.speakerRole() + ":" + speaker.speakerName())
                .containsExactly("SELF:호준", "FRIEND:호석");
        // 화자 2를 본인으로 골랐으므로 화자 1의 발화는 친구(FRIEND)
        assertThat(source.segments()).extracting(segment -> segment.speakerRole())
                .containsExactly(SpeakerRole.FRIEND, SpeakerRole.SELF);
        assertThat(source.segments().get(0).content()).isEqualTo("오늘 하루는 어땠어?");
    }

    @Test
    void 분석_프로세서가_예상하지_못한_예외로_끝나도_실패_상태를_반영한다() throws Exception {
        transcribed(RelationshipType.FRIEND_DAILY);
        doThrow(new IllegalStateException("분석 시작 단계 오류"))
                .when(analysisProcessor)
                .process(recording.getId());

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_1","partnerNickname":"호석"}
                                """))
                .andExpect(status().isOk());

        awaitUnexpectedFailure();

        var analysis = conversationAnalysisRepository.findByRecordingId(recording.getId())
                .orElseThrow();
        Recording failedRecording = recordingRepository.findById(recording.getId()).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.FAILED);
        assertThat(analysis.getFailureReason()).isEqualTo(AnalysisFailureReason.PROCESSING_ERROR);
        assertThat(failedRecording.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(failedRecording.getFailureReason())
                .isEqualTo(RecordingFailureReason.ANALYSIS_FAILED);
    }

    @Test
    void 부모_자녀_대화는_본인이_자녀면_상대가_부모로_매핑된다() throws Exception {
        transcribed(RelationshipType.PARENT_CHILD_CONFLICT);

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_1","partnerNickname":"엄마","selfRole":"CHILD"}
                                """))
                .andExpect(status().isOk());

        AnalysisSource source = analysisSourceReader.read(recording.getId());
        assertThat(source.speakers()).extracting(speaker -> speaker.speakerRole() + ":" + speaker.speakerName())
                .containsExactly("CHILD:호준", "PARENT:엄마");
    }

    @Test
    void 부모_자녀_대화에서_본인_역할이_없으면_400() throws Exception {
        transcribed(RelationshipType.PARENT_CHILD_CONFLICT);

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_1","partnerNickname":"엄마"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORDING400_4"));
        assertThat(applicationEvents.stream(AnalysisRequestedEvent.class)).isEmpty();
    }

    @Test
    void 전사가_끝나지_않았으면_409() throws Exception {
        user = userRepository.save(User.createLocal("mapping@example.com", "encoded", "호준"));
        recording = transcribingRecording(RelationshipType.FRIEND_DAILY);
        transcriptionRepository.save(Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH));

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_1","partnerNickname":"호석"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORDING409_1"));
    }

    @Test
    void 전사에_없는_화자를_고르면_400() throws Exception {
        transcribed(RelationshipType.FRIEND_DAILY);

        mockMvc.perform(post("/api/v1/recordings/{id}/speaker-mapping", recording.getId())
                        .header("Authorization", "Bearer " + token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"selfSpeakerLabel":"SPEAKER_3","partnerNickname":"호석"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("RECORDING400_4"));
    }

    @Test
    void 분석이_완료되면_녹음을_완료로_바꾸고_완료_시각을_기록한다() {
        transcribed(RelationshipType.FRIEND_DAILY);
        Recording analyzing = recordingRepository.findById(recording.getId()).orElseThrow();
        analyzing.mapSpeakers("1", "호석", null);
        recordingRepository.save(analyzing);

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnalysisCompletedEvent(1L, recording.getId())));

        Recording completed = recordingRepository.findById(recording.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(RecordingStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    @Test
    void 분석이_발화_부족으로_실패하면_녹음을_사유와_함께_실패로_바꾼다() {
        analyzingRecording();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnalysisFailedEvent(
                        recording.getId(), AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA)));

        Recording failed = recordingRepository.findById(recording.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(RecordingStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo(RecordingFailureReason.INSUFFICIENT_SPEAKER_DATA);
        assertThat(failed.getFailedAt()).isNotNull(); // 실패 후 3일 보관 기간의 기준 시각
    }

    @Test
    void 분석_처리_오류로_실패하면_분석_실패_사유로_저장한다() {
        analyzingRecording();

        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnalysisFailedEvent(
                        recording.getId(), AnalysisFailureReason.PROCESSING_ERROR)));

        assertThat(recordingRepository.findById(recording.getId()).orElseThrow().getFailureReason())
                .isEqualTo(RecordingFailureReason.ANALYSIS_FAILED);
    }

    @Test
    void 녹음이_없는_분석_완료_이벤트는_무시한다() {
        // 목업 분석처럼 실제 녹음이 없는 경우에도 분석 쪽 흐름을 막지 않는다
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                eventPublisher.publishEvent(new AnalysisCompletedEvent(1L, 999_999L)));
    }

    // 화자 지정까지 끝나 분석 중인 녹음
    private void analyzingRecording() {
        transcribed(RelationshipType.FRIEND_DAILY);
        Recording analyzing = recordingRepository.findById(recording.getId()).orElseThrow();
        analyzing.mapSpeakers("1", "호석", null);
        recordingRepository.save(analyzing);
    }

    // 전사가 끝난 녹음: 화자 1·2의 발화가 저장되어 있다
    private void transcribed(RelationshipType relationshipType) {
        user = userRepository.save(User.createLocal("mapping@example.com", "encoded", "호준"));
        recording = transcribingRecording(relationshipType);
        Transcription transcription = Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH);
        transcription.complete();
        transcriptionRepository.save(transcription);
        transcriptSegmentRepository.saveAll(List.of(
                TranscriptSegment.create(recording.getId(), 1, "1", 1200, 4600, "오늘 하루는 어땠어?"),
                TranscriptSegment.create(recording.getId(), 2, "2", 4800, 7200, "오늘은 그냥 평범했어.")
        ));
    }

    private Recording transcribingRecording(RelationshipType relationshipType) {
        Recording created = Recording.create(user.getId(), "mapping-test.m4a", 600);
        ReflectionTestUtils.setField(created, "relationshipType", relationshipType);
        ReflectionTestUtils.setField(created, "status", RecordingStatus.TRANSCRIBING);
        return recordingRepository.save(created);
    }

    private String token() {
        return jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    private void awaitUnexpectedFailure() throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            boolean analysisFailed = conversationAnalysisRepository
                    .findByRecordingId(recording.getId())
                    .map(analysis -> analysis.getStatus() == AnalysisStatus.FAILED)
                    .orElse(false);
            boolean recordingFailed = recordingRepository.findById(recording.getId())
                    .map(found -> found.getStatus() == RecordingStatus.FAILED)
                    .orElse(false);
            if (analysisFailed && recordingFailed) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("예상하지 못한 분석 실패가 제한 시간 안에 반영되지 않았습니다.");
    }
}
