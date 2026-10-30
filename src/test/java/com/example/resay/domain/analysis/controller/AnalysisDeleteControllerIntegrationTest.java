package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.character.entity.CharacterImage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.entity.RelationshipType;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisDeleteControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private CharacterImageRepository characterImageRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private Recording recording;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                User.createLocal("analysis-delete@example.com", "encoded-password", "삭제사용자")
        );
        User otherUser = userRepository.saveAndFlush(
                User.createLocal("analysis-delete-other@example.com", "encoded-password", "다른사용자")
        );
        recording = createRecording(user.getId());
        accessToken = jwtTokenProvider
                .issueAccessToken(user.getId(), user.getRole().name())
                .value();
        otherUserAccessToken = jwtTokenProvider
                .issueAccessToken(otherUser.getId(), otherUser.getRole().name())
                .value();
    }

    @Test
    void deletesCompletedAnalysisAndCharacterImageMetadataButKeepsRecording() throws Exception {
        ConversationAnalysis analysis = completeAnalysis();
        CharacterImage image = CharacterImage.prepare(analysis.getId(), SpeakerRole.SELF);
        image.start();
        image.complete("10/self.png", "image/png", "test-model", "test-v1");
        characterImageRepository.saveAndFlush(image);

        performDelete(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ANALYSIS200_3"))
                .andExpect(jsonPath("$.result").isEmpty());

        assertThat(conversationAnalysisRepository.findByRecordingId(recording.getId())).isEmpty();
        assertThat(analysisResultRepository.findByAnalysisId(analysis.getId())).isEmpty();
        assertThat(characterImageRepository.findAllByAnalysisId(analysis.getId())).isEmpty();
        assertThat(recordingRepository.findById(recording.getId())).isPresent();

        mockMvc.perform(get("/api/v1/analyses/{recordingId}", recording.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS404_1"));

        mockMvc.perform(get("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty());
    }

    @Test
    void deletesFailedAnalysis() throws Exception {
        analysisService.start(recording.getId());
        analysisService.fail(recording.getId(), AnalysisFailureReason.PROCESSING_ERROR);

        performDelete(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ANALYSIS200_3"));

        assertThat(conversationAnalysisRepository.findByRecordingId(recording.getId())).isEmpty();
    }

    @Test
    void rejectsDeletionWhileAnalysisIsRunning() throws Exception {
        analysisService.start(recording.getId());

        performDelete(accessToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS409_4"));

        assertThat(conversationAnalysisRepository.findByRecordingId(recording.getId())).isPresent();
    }

    @Test
    void rejectsDeletionWhileCharacterImageIsGenerating() throws Exception {
        ConversationAnalysis analysis = completeAnalysis();
        CharacterImage image = CharacterImage.prepare(analysis.getId(), SpeakerRole.SELF);
        image.start();
        characterImageRepository.saveAndFlush(image);

        performDelete(accessToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSIS409_4"));

        assertThat(conversationAnalysisRepository.findByRecordingId(recording.getId())).isPresent();
        assertThat(analysisResultRepository.findByAnalysisId(analysis.getId())).isPresent();
        assertThat(characterImageRepository.findAllByAnalysisId(analysis.getId())).hasSize(1);
    }

    @Test
    void hidesAnalysisOwnedByAnotherUser() throws Exception {
        completeAnalysis();

        performDelete(otherUserAccessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));

        assertThat(conversationAnalysisRepository.findByRecordingId(recording.getId())).isPresent();
    }

    @Test
    void returnsNotFoundWhenAnalysisDoesNotExist() throws Exception {
        performDelete(accessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS404_1"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(delete("/api/v1/analyses/{recordingId}", recording.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void exposesAnalysisDeleteEndpointInOpenApiDocument() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var operation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses/{recordingId}")
                .path("delete");

        assertThat(operation.path("summary").asText()).isEqualTo("분석 결과 삭제");
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private ConversationAnalysis completeAnalysis() {
        analysisService.start(recording.getId());
        analysisService.complete(
                recording.getId(),
                new AnalysisResultCommand("{}", "test-model", "test-v1", "test-v1")
        );
        return conversationAnalysisRepository.findByRecordingId(recording.getId()).orElseThrow();
    }

    private Recording createRecording(Long userId) {
        Recording saved = recordingRepository.saveAndFlush(
                Recording.create(userId, "analysis-delete.m4a", 600)
        );
        saved.selectType(RelationshipType.FRIEND_DAILY);
        return recordingRepository.saveAndFlush(saved);
    }

    private org.springframework.test.web.servlet.ResultActions performDelete(String token)
            throws Exception {
        return mockMvc.perform(delete("/api/v1/analyses/{recordingId}", recording.getId())
                .header("Authorization", "Bearer " + token));
    }
}
