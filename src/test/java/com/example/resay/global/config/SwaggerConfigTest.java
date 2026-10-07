package com.example.resay.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsDeclareBearerAuthForAllApis() throws Exception {
        // 문서 경로 접근 허용 여부는 SecurityConfig 책임이라 여기서는 인증된 요청으로 문서 내용만 확인한다
        mockMvc.perform(get("/v3/api-docs").with(user("tester")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Resay API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.security[0].bearerAuth").isArray());
    }

    // 로그인한 사용자 ID는 토큰에서 꺼낸다. 문서에 요청 파라미터로 나오면 프론트가 직접 보내야 하는 값으로 오해한다
    @Test
    void apiDocsDoNotExposeTokenDerivedUserIdAsRequestParameter() throws Exception {
        String docs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users/me'].get").exists())
                .andReturn().getResponse().getContentAsString();

        assertThat(docs).doesNotContain("\"name\":\"userId\"").doesNotContain("\"name\":\"adminId\"");
    }
}
