package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.service.AnalysisService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisListControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private User user;
    private User otherUser;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                User.createLocal("analysis-list@example.com", "encoded-password", "목록사용자")
        );
        otherUser = userRepository.saveAndFlush(
                User.createLocal("analysis-list-other@example.com", "encoded-password", "다른사용자")
        );
        accessToken = jwtTokenProvider
                .issueAccessToken(user.getId(), user.getRole().name())
                .value();
    }

    @Test
    void returnsOwnedAnalysesInNewestFirstPages() throws Exception {
        Recording older = createRecording(user.getId(), RelationshipType.FRIEND_DAILY);
        Recording newer = createRecording(user.getId(), RelationshipType.COUPLE_CONFLICT);
        Recording others = createRecording(otherUser.getId(), RelationshipType.COUPLE_DAILY);
        analysisService.start(older.getId());
        analysisService.start(newer.getId());
        analysisService.fail(newer.getId(), AnalysisFailureReason.PROCESSING_ERROR);
        analysisService.start(others.getId());

        mockMvc.perform(get("/api/v1/analyses")
                        .param("page", "0")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ANALYSIS200_2"))
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].recordingId").value(newer.getId()))
                .andExpect(jsonPath("$.result.items[0].relationshipType")
                        .value("COUPLE_CONFLICT"))
                .andExpect(jsonPath("$.result.items[0].analysisStatus").value("FAILED"))
                .andExpect(jsonPath("$.result.items[0].failureReason")
                        .value("PROCESSING_ERROR"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(1))
                .andExpect(jsonPath("$.result.totalElements").value(2))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true));

        mockMvc.perform(get("/api/v1/analyses")
                        .param("page", "1")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].recordingId").value(older.getId()))
                .andExpect(jsonPath("$.result.items[0].analysisStatus").value("ANALYZING"))
                .andExpect(jsonPath("$.result.items[0].failureReason").isEmpty())
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void returnsEmptyPageWhenUserHasNoAnalysis() throws Exception {
        mockMvc.perform(get("/api/v1/analyses")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void rejectsInvalidPageRequest() throws Exception {
        mockMvc.perform(get("/api/v1/analyses")
                        .param("size", "51")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analyses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void exposesAnalysisListEndpointInOpenApiDocument() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var operation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses")
                .path("get");

        assertThat(operation.path("summary").asText()).isEqualTo("분석 목록 조회");
        assertThat(operation.path("parameters")).hasSize(2);
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private Recording createRecording(Long userId, RelationshipType relationshipType) {
        Recording recording = recordingRepository.saveAndFlush(
                Recording.create(userId, "analysis-list-" + relationshipType + ".m4a", 600)
        );
        recording.selectType(relationshipType);
        return recordingRepository.saveAndFlush(recording);
    }
}
