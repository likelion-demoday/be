package com.example.resay.domain.auth.controller;

import com.example.resay.domain.auth.entity.RefreshToken;
import com.example.resay.domain.auth.repository.RefreshTokenRepository;
import com.example.resay.domain.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RefreshTokenFlowTest {

    private static final String EMAIL = "user@example.com";
    private static final String PASSWORD = "password123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void loginIssuesRefreshTokenAndStoresOnlyItsHash() throws Exception {
        String refreshToken = signupAndGetRefreshToken();

        login()
                .andExpect(jsonPath("$.result.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.result.refreshExpiresIn").value(14 * 24 * 60 * 60));

        assertThat(refreshTokenRepository.findAll())
                .allSatisfy(saved -> {
                    assertThat(saved.getTokenHash()).hasSize(64).isNotEqualTo(refreshToken);
                    assertThat(saved.getUserId()).isEqualTo(userId());
                });
        assertThat(refreshTokenRepository.findByTokenHash(sha256(refreshToken))).isPresent();
    }

    @Test
    void reissueReturnsNewTokensAndInvalidatesUsedRefreshToken() throws Exception {
        String refreshToken = signupAndGetRefreshToken();

        String body = reissue(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_2"))
                .andReturn().getResponse().getContentAsString();
        String newAccessToken = JsonPath.read(body, "$.result.accessToken");
        String newRefreshToken = JsonPath.read(body, "$.result.refreshToken");

        assertThat(newRefreshToken).isNotEqualTo(refreshToken);
        assertThat(jwtDecoder.decode(newAccessToken).getSubject()).isEqualTo(String.valueOf(userId()));

        // 이미 사용한 토큰은 다시 쓸 수 없다
        reissue(refreshToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_2"));
        // 새로 받은 토큰은 사용할 수 있다
        reissue(newRefreshToken).andExpect(status().isOk());
    }

    @Test
    void reissueRejectsUnknownRefreshToken() throws Exception {
        reissue("unknown-refresh-token")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_2"));
    }

    @Test
    void reissueRejectsExpiredRefreshToken() throws Exception {
        signupAndGetRefreshToken();
        String expiredToken = "expired-refresh-token";
        refreshTokenRepository.saveAndFlush(RefreshToken.create(
                userId(), sha256(expiredToken), Instant.now().minus(1, ChronoUnit.MINUTES)));

        reissue(expiredToken)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_2"));
    }

    @Test
    void logoutRevokesRefreshTokenAndIsIdempotent() throws Exception {
        String refreshToken = signupAndGetRefreshToken();

        logout(refreshToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_3"));
        logout(refreshToken).andExpect(status().isOk());

        reissue(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    void keepsSessionsOfEachDeviceIndependently() throws Exception {
        String firstDevice = signupAndGetRefreshToken();
        String secondDevice = JsonPath.read(
                login().andReturn().getResponse().getContentAsString(), "$.result.refreshToken");

        logout(firstDevice).andExpect(status().isOk());

        reissue(secondDevice).andExpect(status().isOk());
    }

    @Test
    void reissueAndLogoutWorkWithExpiredAccessTokenInHeader() throws Exception {
        String refreshToken = signupAndGetRefreshToken();

        String body = mockMvc.perform(post("/api/v1/auth/reissue")
                        .header("Authorization", "Bearer expired-or-invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshTokenBody(refreshToken)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newRefreshToken = JsonPath.read(body, "$.result.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer expired-or-invalid-access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshTokenBody(newRefreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    void reissueRejectsMissingRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.refreshToken", notNullValue()));
    }

    private String signupAndGetRefreshToken() throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "%s", "nickname": "닉네임"}
                                """.formatted(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.result.refreshToken");
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(EMAIL, PASSWORD)));
    }

    private ResultActions reissue(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/reissue")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshTokenBody(refreshToken)));
    }

    private ResultActions logout(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshTokenBody(refreshToken)));
    }

    private String refreshTokenBody(String refreshToken) {
        return """
                {"refreshToken": "%s"}
                """.formatted(refreshToken);
    }

    private Long userId() {
        return userRepository.findByEmail(EMAIL).orElseThrow().getId();
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
