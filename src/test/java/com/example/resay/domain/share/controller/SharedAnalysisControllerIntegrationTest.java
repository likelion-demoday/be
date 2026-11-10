package com.example.resay.domain.share.controller;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.ConversationAnalysis;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.model.SpeakerMetrics;
import com.example.resay.domain.analysis.model.SpeakerRole;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.service.AnalysisService;
import com.example.resay.domain.character.model.GeneratedCharacterImage;
import com.example.resay.domain.character.port.CharacterImageStorage;
import com.example.resay.domain.character.service.CharacterImageService;
import com.example.resay.domain.recording.entity.Recording;
import com.example.resay.domain.recording.repository.RecordingRepository;
import com.example.resay.domain.share.entity.AnalysisShare;
import com.example.resay.domain.share.repository.AnalysisShareRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SharedAnalysisControllerIntegrationTest {

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
    private AnalysisShareRepository analysisShareRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private CharacterImageService characterImageService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CharacterImageStorage characterImageStorage;

    private User owner;
    private User otherUser;
    private Recording recording;
    private String ownerAccessToken;
    private String otherAccessToken;

    @BeforeEach
    void setUp() throws Exception {
        owner = userRepository.saveAndFlush(
                User.createLocal("share-owner@example.com", "encoded-password", "공유사용자")
        );
        otherUser = userRepository.saveAndFlush(
                User.createLocal("share-other@example.com", "encoded-password", "다른사용자")
        );
        recording = recordingRepository.saveAndFlush(
                Recording.create(owner.getId(), "share-test.m4a", 600)
        );
        ownerAccessToken = token(owner);
        otherAccessToken = token(otherUser);

        analysisService.start(recording.getId());
        analysisService.complete(recording.getId(), completedResult());
    }

    @Test
    void issuesSevenDayLinkAndStoresOnlyTokenHash() throws Exception {
        String rawToken = issue(ownerAccessToken);

        AnalysisShare share = analysisShareRepository.findByRecordingId(recording.getId())
                .orElseThrow();
        ConversationAnalysis analysis = conversationAnalysisRepository
                .findByRecordingId(recording.getId())
                .orElseThrow();
        assertThat(rawToken).hasSize(43);
        assertThat(share.getTokenHash()).hasSize(64).doesNotContain(rawToken);
        assertThat(share.getAnalysisId()).isEqualTo(analysis.getId());
        assertThat(share.getExpiresAt())
                .isAfter(LocalDateTime.now().plusDays(6))
                .isBefore(LocalDateTime.now().plusDays(8));
    }

    @Test
    void refusesToIssueLinkForAnotherUsersRecording() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/analyses/{recordingId}/shares",
                        recording.getId()
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAccessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void returnsSanitizedReportWithoutLogin() throws Exception {
        String rawToken = issue(ownerAccessToken);

        String response = getShared(rawToken)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", "noindex, nofollow, noarchive"))
                .andExpect(jsonPath("$.result.scenario").value("FRIEND_DAILY"))
                .andExpect(jsonPath("$.result.speakerRoles[0]").value("SELF"))
                .andExpect(jsonPath(
                        "$.result.qualitativeAnalysis.characterInsights[0].name"
                ).value("질문 수집가"))
                .andExpect(jsonPath(
                        "$.result.qualitativeAnalysis.representativeEvidences[0]"
                                + ".evidence.displayContent"
                ).value("화면 표시 발화"))
                .andExpect(jsonPath("$.result.qualitativeAnalysis.reportMetrics.length()")
                        .value(8))
                .andExpect(jsonPath("$.result.qualitativeAnalysis.reportMetrics[0].type")
                        .value("SENTENCE_COMPLETENESS"))
                .andExpect(jsonPath("$.result.qualitativeAnalysis.reportMetrics[0].value")
                        .value(0))
                .andExpect(jsonPath("$.result.qualitativeAnalysis.reportMetrics[4].speakerRole")
                        .value("FRIEND"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(response)
                .doesNotContain("recordingId")
                .doesNotContain("speakerName")
                .doesNotContain("segmentId")
                .doesNotContain("originalContent")
                .doesNotContain("원본 비공개 발화")
                .doesNotContain("modelName")
                .doesNotContain("promptVersion")
                .doesNotContain("schemaVersion");
    }

    @Test
    void reissuingLinkImmediatelyInvalidatesOldToken() throws Exception {
        String oldToken = issue(ownerAccessToken);
        String newToken = issue(ownerAccessToken);

        assertThat(newToken).isNotEqualTo(oldToken);
        getShared(oldToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
        getShared(newToken).andExpect(status().isOk());
        assertThat(analysisShareRepository.findAll()).hasSize(1);
    }

    @Test
    void cancelledLinkReturnsSameNotFoundResponse() throws Exception {
        String rawToken = issue(ownerAccessToken);

        mockMvc.perform(delete(
                        "/api/v1/analyses/{recordingId}/shares",
                        recording.getId()
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SHARE200_1"));

        getShared(rawToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
    }

    @Test
    void expiredAndMalformedTokensReturnSameNotFoundResponse() throws Exception {
        String rawToken = issue(ownerAccessToken);
        AnalysisShare share = analysisShareRepository.findByRecordingId(recording.getId())
                .orElseThrow();
        ReflectionTestUtils.setField(share, "expiresAt", LocalDateTime.now().minusSeconds(1));

        getShared(rawToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
        getShared("malformed-token")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
        mockMvc.perform(get("/api/v1/shared/analysis-report"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
    }

    @Test
    void deletingAnalysisRemovesShareAndInvalidatesToken() throws Exception {
        String rawToken = issue(ownerAccessToken);

        mockMvc.perform(delete("/api/v1/analyses/{recordingId}", recording.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerAccessToken))
                .andExpect(status().isOk());

        assertThat(analysisShareRepository.findByRecordingId(recording.getId())).isEmpty();
        getShared(rawToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
    }

    @Test
    void staleShareNeverMovesToAReplacementAnalysisForSameRecording() throws Exception {
        String rawToken = issue(ownerAccessToken);
        ConversationAnalysis oldAnalysis = conversationAnalysisRepository
                .findByRecordingId(recording.getId())
                .orElseThrow();

        analysisResultRepository.findByAnalysisId(oldAnalysis.getId())
                .ifPresent(analysisResultRepository::delete);
        analysisResultRepository.flush();
        conversationAnalysisRepository.delete(oldAnalysis);
        conversationAnalysisRepository.flush();

        analysisService.start(recording.getId());
        analysisService.complete(recording.getId(), completedResult());

        getShared(rawToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE404_1"));
    }

    @Test
    void shareTokenCannotAuthenticateRegularAnalysisApi() throws Exception {
        String rawToken = issue(ownerAccessToken);

        mockMvc.perform(get("/api/v1/analyses/{recordingId}", recording.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Share " + rawToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void servesOnlyCharacterImageBoundToSharedAnalysis() throws Exception {
        String rawToken = issue(ownerAccessToken);
        ConversationAnalysis analysis = conversationAnalysisRepository
                .findByRecordingId(recording.getId())
                .orElseThrow();
        byte[] imageBytes = new byte[]{1, 2, 3, 4};
        characterImageService.prepare(analysis.getId(), List.of(SpeakerRole.SELF));
        characterImageService.begin(analysis.getId(), SpeakerRole.SELF);
        characterImageService.complete(
                analysis.getId(),
                SpeakerRole.SELF,
                "shared/self.png",
                new GeneratedCharacterImage(imageBytes, "image/png", "gpt-image", "v1")
        );
        given(characterImageStorage.load("shared/self.png")).willReturn(imageBytes);

        mockMvc.perform(get("/api/v1/shared/character-image")
                        .queryParam("speakerRole", "SELF")
                        .header(HttpHeaders.AUTHORIZATION, "Share " + rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(imageBytes));
    }

    private String issue(String accessToken) throws Exception {
        String response = mockMvc.perform(post(
                        "/api/v1/analyses/{recordingId}/shares",
                        recording.getId()
                )
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SHARE201_1"))
                .andExpect(jsonPath("$.result.shareUrl").value(
                        org.hamcrest.Matchers.startsWith("https://resay.site/share#")
                ))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        String shareUrl = root.path("result").path("shareUrl").asText();
        return shareUrl.substring(shareUrl.indexOf('#') + 1);
    }

    private ResultActions getShared(String rawToken) throws Exception {
        return mockMvc.perform(get("/api/v1/shared/analysis-report")
                .header(HttpHeaders.AUTHORIZATION, "Share " + rawToken));
    }

    private String token(User user) {
        return jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    private AnalysisResultCommand completedResult() throws Exception {
        AnalysisReport report = new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        recording.getId(),
                        AnalysisScenario.FRIEND_DAILY,
                        600_000L,
                        speakersFor(AnalysisScenario.FRIEND_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(
                        List.of(new SpeakerMetrics(
                                SpeakerRole.SELF,
                                300_000L,
                                20,
                                500,
                                1_737,
                                100,
                                BigDecimal.valueOf(55),
                                BigDecimal.valueOf(15_000),
                                BigDecimal.valueOf(100),
                                BigDecimal.valueOf(5.0)
                        )),
                        null
                ),
                new AnalysisReport.QualitativeReport(
                        new QualitativeAnalysis.Overview(
                                "대화 요약",
                                "공유 화면에 표시할 요약입니다.",
                                List.of(101L)
                        ),
                        List.of(),
                        List.of(),
                        List.of(new QualitativeAnalysis.CharacterInsight(
                                SpeakerRole.SELF,
                                "질문 수집가",
                                "상대의 이야기를 자연스럽게 이어 가요.",
                                List.of(101L)
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(new AnalysisReport.RepresentativeEvidence(
                                QualitativeAnalysis.ReportEvidenceType.FOLLOWING_RESPONSE,
                                SpeakerRole.SELF,
                                null,
                                new AnalysisReport.EvidenceQuote(
                                        101L,
                                        SpeakerRole.SELF,
                                        "원본 비공개 발화",
                                        "화면 표시 발화",
                                        true,
                                        1_000L,
                                        2_000L
                                )
                        ))
                )
        );
        return new AnalysisResultCommand(
                objectMapper.writeValueAsString(report),
                "liner-mark-1.1",
                "analysis-prompt-v21",
                "analysis-report-v14"
        );
    }
}
