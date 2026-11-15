package com.example.resay.domain.recording.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import com.example.resay.global.security.AudioUrlSigner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecordingDetailIntegrationTest {

    @TempDir
    Path tempDir;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AudioUrlSigner audioUrlSigner;

    private User user;
    private Recording recording;

    @BeforeEach
    void setUp() throws Exception {
        Path audioFile = Files.writeString(tempDir.resolve("conversation.m4a"), "audio");
        user = userRepository.save(User.createLocal("detail@example.com", "encoded", "지현"));
        recording = Recording.create(user.getId(), audioFile.toString(), 783);
        ReflectionTestUtils.setField(recording, "relationshipType", RelationshipType.FRIEND_DAILY);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.COMPLETED);
        recordingRepository.save(recording);
    }

    @Test
    void 대화_정보는_주지만_분석이_끝난_음성의_재생_주소는_주지_않는다() throws Exception {
        getDetail(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_7"))
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.title").value(recording.getTitle()))
                .andExpect(jsonPath("$.result.relationshipType").value("FRIEND_DAILY"))
                .andExpect(jsonPath("$.result.durationSeconds").value(783))
                .andExpect(jsonPath("$.result.createdAt").exists())
                .andExpect(jsonPath("$.result.audioAvailable").value(false))
                .andExpect(jsonPath("$.result.audioUrl").doesNotExist());
    }

    @Test
    void 음성이_이미_삭제됐어도_상세_정보는_조회한다() throws Exception {
        recording.markAudioDeleted();

        getDetail(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.audioAvailable").value(false))
                .andExpect(jsonPath("$.result.audioUrl").doesNotExist());
    }

    @Test
    void 분석이_끝나면_파일_삭제가_지연돼도_기존_재생_주소를_차단한다() throws Exception {
        AudioUrlSigner.SignedAudioUrl signed = audioUrlSigner.sign(recording.getId());

        mockMvc.perform(get("/api/v1/recordings/{id}/audio", recording.getId())
                        .param("expires", String.valueOf(signed.expires()))
                        .param("signature", signed.signature()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("RECORDING410_1"));
    }

    @Test
    void 지운_대화는_404() throws Exception {
        ReflectionTestUtils.setField(recording, "deletedAt", LocalDateTime.now());

        getDetail(user)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void 분석이_끝나지_않은_녹음은_404() throws Exception {
        for (RecordingStatus inProgress : new RecordingStatus[]{RecordingStatus.TYPE_SELECTED,
                RecordingStatus.TRANSCRIBING, RecordingStatus.ANALYZING, RecordingStatus.FAILED}) {
            ReflectionTestUtils.setField(recording, "status", inProgress);

            getDetail(user).andExpect(status().isNotFound());
        }
    }

    @Test
    void 다른_사람의_대화는_404() throws Exception {
        User other = userRepository.save(User.createLocal("detail-other@example.com", "encoded", "남"));

        getDetail(other).andExpect(status().isNotFound());
    }

    private ResultActions getDetail(User requester) throws Exception {
        return mockMvc.perform(get("/api/v1/recordings/{id}", recording.getId())
                .header("Authorization", "Bearer " + token(requester)));
    }

    private String token(User requester) {
        return jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
    }
}
