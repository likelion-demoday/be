package com.example.resay.domain.auth.controller;

import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    private static final String SIGNUP_URL = "/api/v1/auth/signup";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    // 인증만 통과하면 핸들러가 없어 404가 나는 경로. 인증 필터 동작 확인용
    private static final String PROTECTED_URL = "/api/v1/protected-resource";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void signupCreatesUserAndReturnsAccessToken() throws Exception {
        String responseBody = signup("User@Example.com ", "password123", "닉네임")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("AUTH201_1"))
                .andExpect(jsonPath("$.result.accessToken", notNullValue()))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.result.expiresIn").value(3600))
                .andReturn().getResponse().getContentAsString();

        User saved = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(saved.getPassword()).isNotEqualTo("password123");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();

        Jwt jwt = jwtDecoder.decode(JsonPath.read(responseBody, "$.result.accessToken"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(saved.getId()));
        assertThat(jwt.getClaimAsString("role")).isEqualTo("USER");
    }

    @Test
    void signupRejectsDuplicateEmailIgnoringCaseAndWhitespace() throws Exception {
        signup("user@example.com", "password123", "닉네임").andExpect(status().isCreated());

        signup("  USER@example.com", "password456", "다른닉네임")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH409_1"));
    }

    @Test
    void signupRejectsInvalidRequest() throws Exception {
        signup("not-an-email", "short", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.error.email", notNullValue()))
                .andExpect(jsonPath("$.error.password", notNullValue()))
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
    }

    @Test
    void signupRecordsAgreements() throws Exception {
        signup("user@example.com", "password123", "닉네임").andExpect(status().isCreated());
        signupWith("""
                {"email": "marketing@example.com", "password": "password123", "nickname": "닉네임",
                 "agreements": {"terms": true, "privacy": true, "voice": true, "marketing": true}}
                """).andExpect(status().isCreated());

        User withoutMarketing = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(withoutMarketing.getTermsAgreedAt()).isNotNull();
        assertThat(withoutMarketing.getMarketingAgreedAt()).isNull();
        User withMarketing = userRepository.findByEmail("marketing@example.com").orElseThrow();
        assertThat(withMarketing.getMarketingAgreedAt()).isEqualTo(withMarketing.getTermsAgreedAt());
    }

    @Test
    void signupRequiresEveryRequiredAgreement() throws Exception {
        signupWith("""
                {"email": "user@example.com", "password": "password123", "nickname": "닉네임"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.agreements", notNullValue()));

        // 보내지 않은 필수 항목도 동의하지 않은 것으로 본다
        signupWith("""
                {"email": "user@example.com", "password": "password123", "nickname": "닉네임",
                 "agreements": {"terms": true, "privacy": false, "marketing": true}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error['agreements.privacy']", notNullValue()))
                .andExpect(jsonPath("$.error['agreements.voice']", notNullValue()))
                .andExpect(jsonPath("$.error['agreements.terms']").doesNotExist());

        assertThat(userRepository.findByEmail("user@example.com")).isEmpty();
    }

    @Test
    void loginReturnsAccessToken() throws Exception {
        signup("user@example.com", "password123", "닉네임").andExpect(status().isCreated());

        login("User@Example.com", "password123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_1"))
                .andExpect(jsonPath("$.result.accessToken", notNullValue()));
    }

    @Test
    void loginRejectsWrongPasswordAndUnknownEmailWithSameResponse() throws Exception {
        signup("user@example.com", "password123", "닉네임").andExpect(status().isCreated());

        login("user@example.com", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
        login("none@example.com", "password123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_1"));
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        String responseBody = signup("user@example.com", "password123", "닉네임")
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(responseBody, "$.result.accessToken");

        mockMvc.perform(get(PROTECTED_URL))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PROTECTED_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
    }

    private ResultActions signup(String email, String password, String nickname) throws Exception {
        return mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s", "nickname": "%s",
                         "agreements": {"terms": true, "privacy": true, "voice": true}}
                        """.formatted(email, password, nickname)));
    }

    private ResultActions signupWith(String body) throws Exception {
        return mockMvc.perform(post(SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post(LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)));
    }
}
