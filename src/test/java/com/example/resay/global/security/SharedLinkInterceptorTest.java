package com.example.resay.global.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 제한 기록은 테스트 사이에 초기화되지 않으므로, 테스트마다 서로 다른 IP를 쓴다
@SpringBootTest(properties = {
        "rate-limit.enabled=true",
        "rate-limit.shared.max-requests-per-ip=3",
        "rate-limit.shared.window=1m"
})
@AutoConfigureMockMvc
@Import(SharedLinkInterceptorTest.SharedPingController.class)
class SharedLinkInterceptorTest {

    private static final String SHARED_URL = "/api/v1/shared/ping";

    @Autowired
    private MockMvc mockMvc;

    // C의 공유 API 대신 경로 보호만 확인하기 위한 컨트롤러
    @RestController
    static class SharedPingController {

        @GetMapping(SHARED_URL)
        String ping() {
            return "ok";
        }
    }

    @Test
    void opensWithoutLoginAndAddsPrivacyHeaders() throws Exception {
        mockMvc.perform(from("10.0.0.1", get(SHARED_URL).header("Authorization", "Share some-share-token")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Robots-Tag", containsString("noindex")));
    }

    // 로그인한 사용자가 만료된 토큰을 붙여 와도 공유 페이지는 열린다
    @Test
    void ignoresBearerToken() throws Exception {
        mockMvc.perform(from("10.0.0.2", get(SHARED_URL).header("Authorization", "Bearer expired.or.invalid")))
                .andExpect(status().isOk());
    }

    // 공유 토큰으로는 다른 API에 접근할 수 없고, 공유 경로 밖은 계속 로그인이 필요하다
    @Test
    void doesNotOpenOtherApis() throws Exception {
        mockMvc.perform(from("10.0.0.3", get("/api/v1/users/me").header("Authorization", "Share some-share-token")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(from("10.0.0.3", get("/api/v1/analyses/1")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void limitsRequestsPerIp() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(from("10.0.0.4", get(SHARED_URL))).andExpect(status().isOk());
        }
        mockMvc.perform(from("10.0.0.4", get(SHARED_URL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));

        // 다른 IP는 영향을 받지 않는다
        mockMvc.perform(from("10.0.0.5", get(SHARED_URL))).andExpect(status().isOk());
    }

    // 공유 경로가 아닌 API는 세지 않는다
    @Test
    void doesNotLimitOtherPaths() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(from("10.0.0.6", get("/actuator/health"))).andExpect(status().isOk());
        }
        mockMvc.perform(from("10.0.0.6", get(SHARED_URL))).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder from(String clientIp, MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
    }
}
