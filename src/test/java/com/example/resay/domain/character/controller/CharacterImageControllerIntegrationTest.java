package com.example.resay.domain.character.controller;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.character.entity.CharacterImageStatus;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageStorage;
import com.example.resay.domain.character.repository.CharacterImageRepository;
import com.example.resay.domain.character.service.CharacterImageService;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "character.image.generation-enabled=true")
@AutoConfigureMockMvc
@Transactional
class CharacterImageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private CharacterImageRepository characterImageRepository;

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CharacterImageStorage characterImageStorage;

    private User owner;
    private Recording recording;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {
        owner = userRepository.saveAndFlush(
                User.createLocal("character-owner@example.com", "encoded-password", "소유자")
        );
        User otherUser = userRepository.saveAndFlush(
                User.createLocal("character-other@example.com", "encoded-password", "다른사용자")
        );
        recording = recordingRepository.saveAndFlush(
                Recording.create(owner.getId(), "character-test.m4a", 600)
        );
        accessToken = token(owner);
        otherUserAccessToken = token(otherUser);
    }

    @Test
    void requestsCharacterImagesAndUsesFreeChanceOnlyOnce() throws Exception {
        completeAnalysis();

        performRequest(accessToken)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.code").value("CHARACTER202_1"))
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.alreadyRequested").value(false));

        assertThat(characterImageRepository.findAll()).hasSize(2)
                .allMatch(image -> image.getStatus() == CharacterImageStatus.PENDING);
        assertThat(creditQueryService.getSummary(owner.getId()).characterFreeAvailable()).isFalse();

        performRequest(accessToken)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.result.alreadyRequested").value(true));
        assertThat(characterImageRepository.findAll()).hasSize(2);
    }

    @Test
    void rejectsAnotherUsersRequestWithoutRevealingRecording() throws Exception {
        completeAnalysis();

        performRequest(otherUserAccessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void rejectsRequestBeforeAnalysisCompletion() throws Exception {
        analysisService.start(recording.getId());

        performRequest(accessToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARACTER409_1"));
        assertThat(creditQueryService.getSummary(owner.getId()).characterFreeAvailable()).isTrue();
    }

    @Test
    void returnsEmptyImageListBeforeRequest() throws Exception {
        completeAnalysis();

        mockMvc.perform(get(
                        "/api/v1/analyses/{recordingId}/character-images",
                        recording.getId()
                )
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("CHARACTER200_1"))
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.images").isEmpty());
    }

    @Test
    void returnsGenerationStatusAndCompletedImageContent() throws Exception {
        completeAnalysis();
        performRequest(accessToken).andExpect(status().isAccepted());

        Long analysisId = characterImageRepository.findAll().get(0).getAnalysisId();
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        GeneratedCharacterImage generatedImage = new GeneratedCharacterImage(
                imageBytes,
                "image/png",
                "gpt-image",
                "v1"
        );
        characterImageService.begin(analysisId, SpeakerRole.SELF);
        characterImageService.complete(
                analysisId,
                SpeakerRole.SELF,
                "1/self.png",
                generatedImage
        );
        given(characterImageStorage.load("1/self.png")).willReturn(imageBytes);

        mockMvc.perform(get(
                        "/api/v1/analyses/{recordingId}/character-images",
                        recording.getId()
                )
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.images.length()").value(2))
                .andExpect(jsonPath(
                        "$.result.images[?(@.speakerRole == 'SELF')].status"
                ).value("COMPLETED"))
                .andExpect(jsonPath(
                        "$.result.images[?(@.speakerRole == 'SELF')].contentUrl"
                ).value("/api/v1/analyses/" + recording.getId()
                        + "/character-images/SELF/content"))
                .andExpect(jsonPath(
                        "$.result.images[?(@.speakerRole == 'FRIEND')].status"
                ).value("PENDING"))
                .andExpect(jsonPath(
                        "$.result.images[?(@.speakerRole == 'FRIEND')].contentUrl"
                ).value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())));

        mockMvc.perform(get(
                        "/api/v1/analyses/{recordingId}/character-images/{speakerRole}/content",
                        recording.getId(),
                        SpeakerRole.SELF
                )
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(imageBytes));
    }

    @Test
    void rejectsImageContentThatIsNotReady() throws Exception {
        completeAnalysis();
        performRequest(accessToken).andExpect(status().isAccepted());

        mockMvc.perform(get(
                        "/api/v1/analyses/{recordingId}/character-images/{speakerRole}/content",
                        recording.getId(),
                        SpeakerRole.SELF
                )
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHARACTER409_2"));
    }

    @Test
    void rejectsAnotherUsersImageLookupWithoutRevealingRecording() throws Exception {
        completeAnalysis();

        mockMvc.perform(get(
                        "/api/v1/analyses/{recordingId}/character-images",
                        recording.getId()
                )
                .header("Authorization", "Bearer " + otherUserAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void exposesCharacterImageRequestInSwagger() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var operation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses/{recordingId}/character-images")
                .path("post");

        assertThat(operation.path("summary").asText())
                .isEqualTo("캐릭터 이미지 생성 요청");
        assertThat(operation.path("responses").has("202")).isTrue();

        var getOperation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses/{recordingId}/character-images")
                .path("get");
        var contentOperation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses/{recordingId}/character-images/{speakerRole}/content")
                .path("get");

        assertThat(getOperation.path("summary").asText())
                .isEqualTo("캐릭터 이미지 상태 조회");
        assertThat(contentOperation.path("summary").asText())
                .isEqualTo("캐릭터 이미지 파일 조회");
    }

    private org.springframework.test.web.servlet.ResultActions performRequest(String token)
            throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/analyses/{recordingId}/character-images",
                        recording.getId()
                )
                .header("Authorization", "Bearer " + token));
    }

    private void completeAnalysis() throws Exception {
        analysisService.start(recording.getId());
        analysisService.complete(
                recording.getId(),
                new AnalysisResultCommand(
                        objectMapper.writeValueAsString(report()),
                        "liner-mark-1.1",
                        "analysis-prompt-v9",
                        "analysis-report-v8"
                )
        );
    }

    private AnalysisReport report() {
        return new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        recording.getId(),
                        AnalysisScenario.FRIEND_DAILY,
                        600_000L,
                        speakersFor(AnalysisScenario.FRIEND_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null),
                new AnalysisReport.QualitativeReport(
                        null,
                        List.of(),
                        List.of(),
                        List.of(
                                character(SpeakerRole.SELF, "차분한 탐험가"),
                                character(SpeakerRole.FRIEND, "활기찬 응원가")
                        ),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
    }

    private QualitativeAnalysis.CharacterInsight character(SpeakerRole role, String name) {
        return new QualitativeAnalysis.CharacterInsight(
                role,
                name,
                "대화에서 관찰된 캐릭터 설명",
                List.of(1L)
        );
    }

    private String token(User user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }
}
