package com.example.resay.domain.transcription.controller;

import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RecordingFailureReason;
import com.example.resay.domain.recording.entity.RecordingStatus;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.transcription.entity.Transcription;
import com.example.resay.domain.transcription.entity.TranscriptionProvider;
import com.example.resay.domain.transcription.repository.TranscriptionRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RecordingStatusIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private Recording recording;

    @BeforeEach
    void setUp() {
        user = userRepository.save(User.createLocal("status@example.com", "encoded", "호준"));
        recording = Recording.create(user.getId(), "status-test.m4a", 600);
        ReflectionTestUtils.setField(recording, "status", RecordingStatus.TRANSCRIBING);
        recordingRepository.save(recording);
    }

    @Test
    void 전사_중이면_TRANSCRIBING() throws Exception {
        transcriptionRepository.save(Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH));

        getStatus(user)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("RECORDING200_3"))
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.step").value("TRANSCRIBING"))
                .andExpect(jsonPath("$.result.failureReason").doesNotExist());
    }

    @Test
    void 전사가_끝났으면_화자_선택_단계() throws Exception {
        Transcription transcription = Transcription.prepare(recording.getId(), TranscriptionProvider.CLOVA_SPEECH);
        transcription.complete();
        transcriptionRepository.save(transcription);

        getStatus(user).andExpect(jsonPath("$.result.step").value("SPEAKER_SELECTION_REQUIRED"));
    }

    @Test
    void 실패했으면_실패_사유를_함께_준다() throws Exception {
        recording.fail(RecordingFailureReason.SPEAKER_NOT_SEPARATED);

        getStatus(user)
                .andExpect(jsonPath("$.result.step").value("FAILED"))
                .andExpect(jsonPath("$.result.failureReason").value("SPEAKER_NOT_SEPARATED"));
    }

    @Test
    void 다른_사람의_녹음이면_404() throws Exception {
        User other = userRepository.save(User.createLocal("other@example.com", "encoded", "남"));

        getStatus(other)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    private ResultActions getStatus(User requester) throws Exception {
        String token = jwtTokenProvider.issueAccessToken(requester.getId(), requester.getRole().name()).value();
        return mockMvc.perform(get("/api/v1/recordings/{id}/status", recording.getId())
                .header("Authorization", "Bearer " + token));
    }
}
