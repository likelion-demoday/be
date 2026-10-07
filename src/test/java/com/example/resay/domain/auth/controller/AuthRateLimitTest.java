package com.example.resay.domain.auth.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 제한 기록은 테스트 사이에 초기화되지 않으므로, 테스트마다 서로 다른 IP와 이메일을 쓴다
@SpringBootTest(properties = {
        "rate-limit.enabled=true",
        "rate-limit.login.max-failures-per-ip=6",
        "rate-limit.login.max-failures-per-email=3",
        "rate-limit.login.window=10m",
        "rate-limit.signup.max-attempts-per-ip=4",
        "rate-limit.signup.window=1h",
        "rate-limit.email-check.max-attempts-per-ip=5",
        "rate-limit.email-check.window=10m",
        // 운영과 같이 프록시가 넘겨준 X-Forwarded-For를 접속 IP로 인식한다
        "server.forward-headers-strategy=framework"
})
@AutoConfigureMockMvc
@Transactional
class AuthRateLimitTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String PASSWORD = "password123";
    private static final String WRONG_PASSWORD = "wrong-password";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void blocksLoginAfterRepeatedFailuresEvenWithCorrectPassword() throws Exception {
        String email = "locked@example.com";
        signup("10.0.1.1", email).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            login("10.0.1.2", email, WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_1"));
        }

        login("10.0.1.2", email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON429_1"))
                .andExpect(jsonPath("$.result").doesNotExist())
                .andExpect(jsonPath("$.error.retryAfterSeconds", greaterThan(0)))
                .andExpect(jsonPath("$.error.retryAfterSeconds", lessThanOrEqualTo(600)))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void emailLimitAppliesAcrossDifferentIps() throws Exception {
        String email = "rotating-ip@example.com";
        signup("10.0.2.1", email).andExpect(status().isCreated());

        login("10.0.2.2", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.2.3", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.2.4", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());

        login("10.0.2.5", email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void emailLimitIgnoresCaseAndSurroundingWhitespace() throws Exception {
        login("10.0.3.1", "Mixed@Example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.3.1", "mixed@example.com ", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.3.1", " MIXED@EXAMPLE.COM", WRONG_PASSWORD).andExpect(status().isUnauthorized());

        login("10.0.3.1", "mixed@example.com", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    // 가입된 이메일만 막히면 응답 차이로 가입 여부를 알아낼 수 있다
    @Test
    void unknownEmailIsLimitedTheSameWay() throws Exception {
        String email = "nobody@example.com";

        for (int i = 0; i < 3; i++) {
            login("10.0.4.1", email, WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH401_1"));
        }

        login("10.0.4.1", email, WRONG_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON429_1"));
    }

    @Test
    void successfulLoginClearsFailuresOfThatEmail() throws Exception {
        String email = "typo@example.com";
        signup("10.0.5.1", email).andExpect(status().isCreated());

        login("10.0.5.2", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.5.2", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.5.2", email, PASSWORD).andExpect(status().isOk());

        // 성공 전의 실패 2회가 남아 있었다면 여기서 429가 된다
        login("10.0.5.2", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.5.2", email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.5.2", email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void blocksIpAfterFailuresAcrossManyEmails() throws Exception {
        for (int i = 0; i < 6; i++) {
            login("10.0.6.1", "stuffing-" + i + "@example.com", WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized());
        }

        login("10.0.6.1", "stuffing-new@example.com", WRONG_PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON429_1"));
        // 다른 IP는 영향받지 않는다
        login("10.0.6.2", "stuffing-new@example.com", WRONG_PASSWORD)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blockedIpCannotLoginEvenWithCorrectPassword() throws Exception {
        String email = "victim-of-ip@example.com";
        signup("10.0.7.1", email).andExpect(status().isCreated());
        for (int i = 0; i < 6; i++) {
            login("10.0.7.2", "guess-" + i + "@example.com", WRONG_PASSWORD)
                    .andExpect(status().isUnauthorized());
        }

        login("10.0.7.2", email, PASSWORD).andExpect(status().isTooManyRequests());
        login("10.0.7.3", email, PASSWORD).andExpect(status().isOk());
    }

    // 같은 IP를 여러 사람이 쓰는 환경(학교 · 회사)에서 정상 로그인이 한도를 채우면 안 된다
    @Test
    void successfulLoginsDoNotCountTowardIpLimit() throws Exception {
        String email = "frequent@example.com";
        signup("10.0.8.1", email).andExpect(status().isCreated());

        for (int i = 0; i < 10; i++) {
            login("10.0.8.2", email, PASSWORD).andExpect(status().isOk());
        }
    }

    @Test
    void requestsToAlreadyBlockedEmailDoNotCountTowardIpLimit() throws Exception {
        String blockedEmail = "already-blocked@example.com";
        for (int i = 0; i < 3; i++) {
            login("10.0.9.1", blockedEmail, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
        // IP 실패는 지금까지 3회. 막힌 이메일로 더 보내도 IP 한도(6회)에 가까워지지 않는다
        for (int i = 0; i < 10; i++) {
            login("10.0.9.1", blockedEmail, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
        }

        login("10.0.9.1", "other-1@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.9.1", "other-2@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.9.1", "other-3@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        login("10.0.9.1", "other-4@example.com", WRONG_PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("10.0.10.1", "", "").andExpect(status().isBadRequest());
        }

        login("10.0.10.1", "valid@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEmailLongerThanSignupAllows() throws Exception {
        String longEmail = "a".repeat(101) + "@example.com";

        login("10.0.11.1", longEmail, WRONG_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    void blocksSignupAfterTooManyAttemptsFromSameIp() throws Exception {
        for (int i = 0; i < 4; i++) {
            signup("10.0.12.1", "bulk-" + i + "@example.com").andExpect(status().isCreated());
        }

        signup("10.0.12.1", "bulk-new@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON429_1"))
                .andExpect(jsonPath("$.error.retryAfterSeconds", greaterThan(0)))
                .andExpect(jsonPath("$.error.retryAfterSeconds", lessThanOrEqualTo(3600)))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        signup("10.0.12.2", "bulk-new@example.com").andExpect(status().isCreated());
    }

    // 중복 이메일 응답으로 가입 여부를 대량으로 확인하는 것도 같은 한도로 막는다
    @Test
    void duplicateSignupAttemptsAreCounted() throws Exception {
        String email = "exists@example.com";
        signup("10.0.13.1", email).andExpect(status().isCreated());

        for (int i = 0; i < 4; i++) {
            signup("10.0.13.2", email).andExpect(status().isConflict());
        }

        signup("10.0.13.2", email).andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidSignupRequestsAreNotCounted() throws Exception {
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(from("10.0.14.1", post(SIGNUP_URL))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"not-an-email\",\"password\":\"short\",\"nickname\":\"\"}"))
                    .andExpect(status().isBadRequest());
        }

        signup("10.0.14.1", "after-invalid@example.com").andExpect(status().isCreated());
    }

    @Test
    void signupLimitDoesNotAffectLogin() throws Exception {
        String email = "login-after-signups@example.com";
        signup("10.0.15.1", email).andExpect(status().isCreated());
        for (int i = 0; i < 3; i++) {
            signup("10.0.15.1", "filler-" + i + "@example.com").andExpect(status().isCreated());
        }
        signup("10.0.15.1", "over@example.com").andExpect(status().isTooManyRequests());

        login("10.0.15.1", email, PASSWORD).andExpect(status().isOk());
    }

    // 운영에서는 모든 요청이 프록시(Caddy)를 거쳐 들어온다. 프록시 IP 하나로 묶이면 전원이 함께 차단된다
    @Test
    void countsByForwardedClientIpBehindProxy() throws Exception {
        for (int i = 0; i < 6; i++) {
            loginThroughProxy("203.0.113.5", "proxy-" + i + "@example.com")
                    .andExpect(status().isUnauthorized());
        }

        loginThroughProxy("203.0.113.5", "proxy-new@example.com").andExpect(status().isTooManyRequests());
        loginThroughProxy("203.0.113.6", "proxy-new@example.com").andExpect(status().isUnauthorized());
    }

    @Test
    void exposesRetryAfterHeaderToBrowser() throws Exception {
        for (int i = 0; i < 3; i++) {
            login("10.0.16.1", "cors@example.com", WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(from("10.0.16.1", post(LOGIN_URL))
                        // src/test/resources/application.yml 의 cors.allowed-origins
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("cors@example.com", WRONG_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After"));
    }

    // 가입 여부를 알려주는 API라 한 IP에서 대량으로 조회하지 못하게 한다
    @Test
    void blocksEmailCheckAfterTooManyRequestsFromSameIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            checkEmail("10.0.17.1", "probe-" + i + "@example.com").andExpect(status().isOk());
        }

        checkEmail("10.0.17.1", "probe-new@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON429_1"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        checkEmail("10.0.17.2", "probe-new@example.com").andExpect(status().isOk());
    }

    @Test
    void emailCheckLimitDoesNotAffectSignupOrLogin() throws Exception {
        for (int i = 0; i < 5; i++) {
            checkEmail("10.0.18.1", "busy-" + i + "@example.com").andExpect(status().isOk());
        }
        checkEmail("10.0.18.1", "busy-new@example.com").andExpect(status().isTooManyRequests());

        signup("10.0.18.1", "after-checks@example.com").andExpect(status().isCreated());
        login("10.0.18.1", "after-checks@example.com", PASSWORD).andExpect(status().isOk());
    }

    private ResultActions checkEmail(String clientIp, String email) throws Exception {
        return mockMvc.perform(from(clientIp, post("/api/v1/auth/email/check"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private ResultActions signup(String clientIp, String email) throws Exception {
        return mockMvc.perform(from(clientIp, post(SIGNUP_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"tester\"}".formatted(email, PASSWORD)));
    }

    private ResultActions login(String clientIp, String email, String password) throws Exception {
        return mockMvc.perform(from(clientIp, post(LOGIN_URL))
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, password)));
    }

    private ResultActions loginThroughProxy(String forwardedFor, String email) throws Exception {
        return mockMvc.perform(from("172.18.0.4", post(LOGIN_URL))
                .header("X-Forwarded-For", forwardedFor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(email, WRONG_PASSWORD)));
    }

    private MockHttpServletRequestBuilder from(String clientIp, MockHttpServletRequestBuilder builder) {
        return builder.with(request -> {
            request.setRemoteAddr(clientIp);
            return request;
        });
    }

    private String loginBody(String email, String password) {
        return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
    }
}
