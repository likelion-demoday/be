package com.example.resay.domain.analysis.controller;

import com.example.resay.domain.analysis.repository.AnalysisResultRepository;
import com.example.resay.domain.analysis.repository.ConversationAnalysisRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "analysis.mock.model-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("local")
class MockAnalysisControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalysisResultRepository analysisResultRepository;

    @Autowired
    private ConversationAnalysisRepository conversationAnalysisRepository;

    @AfterEach
    void cleanUp() {
        analysisResultRepository.deleteAll();
        conversationAnalysisRepository.deleteAll();
    }

    @Test
    void createsAndReadsCompletedMockReport() throws Exception {
        String responseBody = mockMvc.perform(post("/api/v1/mock/analyses")
                        .with(user("mock-user"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scenario":"FRIEND_DAILY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.report.recordingInfo.scenario").value("FRIEND_DAILY"))
                .andExpect(jsonPath("$.result.report.quantitativeAnalysis.speakers.length()").value(2))
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.overview.title").isNotEmpty())
                .andExpect(jsonPath("$.result.report.qualitativeAnalysis.timeline[0].startMs").isNumber())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode response = objectMapper.readTree(responseBody);
        long recordingId = response.get("result").get("recordingId").asLong();
        assertThat(recordingId).isPositive();

        mockMvc.perform(get("/api/v1/mock/analyses/{recordingId}", recordingId)
                        .with(user("mock-user")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recordingId").value(recordingId))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.modelName").value("mock-analysis-model"))
                .andExpect(jsonPath("$.result.schemaVersion").value("analysis-report-v2"));
    }
}
