package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.dto.AnalysisResultCommand;
import com.example.resay.domain.analysis.entity.AnalysisFailureReason;
import com.example.resay.domain.analysis.model.AnalysisReport;
import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.QualitativeAnalysis;
import com.example.resay.domain.analysis.service.AnalysisService;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.resay.domain.analysis.support.AnalysisTestSpeakers.speakersFor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AnalysisControllerIntegrationTest {

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

    private Recording recording;
    private String accessToken;
    private String otherUserAccessToken;

    @BeforeEach
    void setUp() {
        User user = userRepository.saveAndFlush(
                User.createLocal("analysis-owner@example.com", "encoded-password", "분석사용자")
        );
        User otherUser = userRepository.saveAndFlush(
                User.createLocal("analysis-other@example.com", "encoded-password", "다른사용자")
        );
        recording = recordingRepository.saveAndFlush(
                Recording.create(user.getId(), "analysis-test.m4a", 600)
        );
        accessToken = jwtTokenProvider
                .issueAccessToken(user.getId(), user.getRole().name())
                .value();
        otherUserAccessToken = jwtTokenProvider
                .issueAccessToken(otherUser.getId(), otherUser.getRole().name())
                .value();
    }

    @Test
    void returnsAnalyzingStatusWithoutReport() throws Exception {
        analysisService.start(recording.getId());

        performGet(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ANALYSIS200_1"))
                .andExpect(jsonPath("$.result.recordingId").value(recording.getId()))
                .andExpect(jsonPath("$.result.status").value("ANALYZING"))
                .andExpect(jsonPath("$.result.failureReason").isEmpty())
                .andExpect(jsonPath("$.result.report").isEmpty())
                .andExpect(jsonPath("$.result.modelName").isEmpty());
    }

    @Test
    void returnsCompletedReportWithoutEvidenceFields() throws Exception {
        analysisService.start(recording.getId());
        analysisService.complete(recording.getId(), completedResult());

        performGet(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.failureReason").isEmpty())
                .andExpect(jsonPath("$.result.report.recordingInfo.recordingId")
                        .value(recording.getId()))
                .andExpect(jsonPath("$.result.report.recordingInfo.speakers[0].speakerRole")
                        .value("SELF"))
                .andExpect(jsonPath("$.result.report.recordingInfo.speakers[0].speakerName")
                        .value("호준"))
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.overview.title")
                        .value("연락 방식 조율"))
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.overview.evidenceSegmentIds")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.timeline[0].evidenceSegmentIds")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.topics[0].segmentIds")
                        .doesNotExist())
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.topics[0].timeRanges[0].startMs")
                        .value(100))
                .andExpect(jsonPath("$.result.modelName").value("liner-mark-1.1"));
    }

    @Test
    void returnsFailedStatusWithoutReport() throws Exception {
        analysisService.start(recording.getId());
        analysisService.fail(recording.getId(), AnalysisFailureReason.PROCESSING_ERROR);

        performGet(accessToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("FAILED"))
                .andExpect(jsonPath("$.result.failureReason").value("PROCESSING_ERROR"))
                .andExpect(jsonPath("$.result.report").isEmpty());
    }

    @Test
    void hidesRecordingOwnedByAnotherUser() throws Exception {
        analysisService.start(recording.getId());

        performGet(otherUserAccessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORDING404_1"));
    }

    @Test
    void returnsNotFoundWhenAnalysisHasNotStarted() throws Exception {
        performGet(accessToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSIS404_1"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/analyses/{recordingId}", recording.getId()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void exposesAnalysisReportEndpointInOpenApiDocument() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var operation = objectMapper.readTree(apiDocs)
                .path("paths")
                .path("/api/v1/analyses/{recordingId}")
                .path("get");

        assertThat(operation.path("summary").asText())
                .isEqualTo("분석 상태 및 보고서 조회");
        assertThat(operation.path("parameters")).hasSize(1);
        assertThat(operation.path("parameters").get(0).path("name").asText())
                .isEqualTo("recordingId");
        assertThat(operation.path("responses").has("200")).isTrue();
    }

    private org.springframework.test.web.servlet.ResultActions performGet(String token)
            throws Exception {
        return mockMvc.perform(get("/api/v1/analyses/{recordingId}", recording.getId())
                .header("Authorization", "Bearer " + token));
    }

    private AnalysisResultCommand completedResult() throws Exception {
        AnalysisReport report = new AnalysisReport(
                new AnalysisReport.RecordingInfo(
                        recording.getId(),
                        AnalysisScenario.FRIEND_DAILY,
                        600_000L,
                        speakersFor(AnalysisScenario.FRIEND_DAILY)
                ),
                new AnalysisReport.QuantitativeAnalysis(List.of(), null),
                new AnalysisReport.QualitativeReport(
                        new QualitativeAnalysis.Overview(
                                "연락 방식 조율",
                                "연락 방식을 함께 정했습니다.",
                                List.of(1L)
                        ),
                        List.of(new AnalysisReport.TimelineItem(
                                "연락 이야기",
                                "연락에 관해 이야기했습니다.",
                                List.of(1L),
                                100L,
                                500L
                        )),
                        List.of(new AnalysisReport.TopicItem(
                                "연락",
                                "연락 방식을 조율했습니다.",
                                List.of(1L),
                                List.of(new AnalysisReport.TopicTimeRange(100L, 500L)),
                                1,
                                400L,
                                true
                        )),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )
        );
        return new AnalysisResultCommand(
                objectMapper.writeValueAsString(report),
                "liner-mark-1.1",
                "analysis-prompt-v4",
                "analysis-report-v5"
        );
    }
}
