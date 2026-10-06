package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.TranscriptSegment;
import com.example.resay.domain.transcription.repository.TranscriptSegmentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationDeletionIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptSegmentRepository transcriptSegmentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AnalysisService analysisService;

    private User user;
    private Recording recording;
    private Path audioFile;

    @BeforeEach
    void setUp() throws Exception {
        audioFile = Files.writeString(tempDir.resolve("conversation.m4a"), "audio");
        user = userRepository.save(User.createLocal("delete@example.com", "encoded", "호준"));
        recording = Recording.create(user.getId(), audioFile.toString(), 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.COMPLETED);
        recordingRepository.save(recording);
        transcriptSegmentRepository.saveAll(List.of(
                TranscriptSegment.create(recording.getId(), 1, "1", 1200, 4600, "오늘 하루는 어땠어?"),
                TranscriptSegment.create(recording.getId(), 2, "2", 4800, 7200, "오늘은 그냥 평범했어.")
        ));
    }

    @Test
    void 분석이_끝난_대화를_지우면_음성과_전사_원문을_지우고_녹음은_숨긴다() throws Exception {
        delete(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_5"));

        assertThat(Files.exists(audioFile)).isFalse();
        assertThat(transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId())).isEmpty();
        assertThat(recording.isDeleted()).isTrue();
        assertThat(recording.hasAudio()).isFalse();
        // 보고서 조회·삭제(분석 쪽)는 이 조회로 소유자를 확인하므로 녹음 행은 남아 있어야 한다
        assertThat(recordingRepository.findByIdAndUserId(recording.getId(), user.getId())).isPresent();
    }

    @Test
    void 지운_대화는_상태_조회와_재삭제에서_없는_것으로_본다() throws Exception {
        delete(user).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/recordings/{id}/status", recording.getId())
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isNotFound());
        delete(user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void 실패한_대화도_지울_수_있다() throws Exception {
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.FAILED);

        delete(user).andExpect(status().isOk());

        assertThat(recording.isDeleted()).isTrue();
    }

    @Test
    void 대화를_지워도_분석_보고서_조회와_분석_목록에는_그대로_남는다() throws Exception {
        // 분석 쪽 기록 (보고서 내용 구조와 무관하게 확인하려고 실패한 분석 기록을 쓴다)
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.FAILED);
        analysisService.start(recording.getId());
        analysisService.fail(recording.getId(), AnalysisFailureReason.INSUFFICIENT_SPEAKER_DATA);

        delete(user).andExpect(status().isOk());

        // 보고서 조회: 녹음 소유자 확인을 통과해 열린다
        mockMvc.perform(get("/api/v1/analyses/{id}", recording.getId())
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.status").value("FAILED"));
        // 분석 목록: 그대로 보인다
        mockMvc.perform(get("/api/v1/analyses")
                        .header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].recordingId").value(recording.getId()));
    }

    @Test
    void 분석_중인_대화는_지울_수_없다() throws Exception {
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.ANALYZING);

        delete(user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RECORDING409_2"));

        assertThat(Files.exists(audioFile)).isTrue();
        assertThat(transcriptSegmentRepository.findByRecordingIdOrderBySegmentNo(recording.getId())).hasSize(2);
        assertThat(recording.isDeleted()).isFalse();
    }

    @Test
    void 다른_사람의_대화는_지울_수_없다() throws Exception {
        User other = userRepository.save(User.createLocal("delete-other@example.com", "encoded", "남"));

        delete(other).andExpect(status().isNotFound());

        assertThat(Files.exists(audioFile)).isTrue();
    }

    private ResultActions delete(User requester) throws Exception {
        return mockMvc.perform(
                MockMvcRequestBuilders.delete("/api/v1/recordings/{id}", recording.getId())
                        .header("Authorization", "Bearer " + token(requester)));
    }

    private String token(User requester) {
        return jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
    }
}
