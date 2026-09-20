package com.example.resay.domain.user.controller;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    private static final String ME_URL = "/api/v1/users/me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(
                User.createLocal("user@example.com", "encoded-password", "닉네임"));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @Test
    void returnsMyInfo() throws Exception {
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER200_1"))
                .andExpect(jsonPath("$.result.id").value(user.getId()))
                .andExpect(jsonPath("$.result.email").value("user@example.com"))
                .andExpect(jsonPath("$.result.nickname").value("닉네임"))
                .andExpect(jsonPath("$.result.provider").value("LOCAL"))
                .andExpect(jsonPath("$.result.createdAt", notNullValue()))
                // 비밀번호 같은 내부 정보는 내려가지 않는다
                .andExpect(jsonPath("$.result.password").doesNotExist());
    }

    @Test
    void returnsNullEmailForSocialAccountWithoutEmail() throws Exception {
        User socialUser = userRepository.saveAndFlush(
                User.createSocial(Provider.KAKAO, "1234567890", null, "카카오유저"));
        String socialToken = jwtTokenProvider
                .issueAccessToken(socialUser.getId(), socialUser.getRole().name()).value();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + socialToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email", nullValue()))
                .andExpect(jsonPath("$.result.provider").value("KAKAO"));
    }

    @Test
    void requiresAccessToken() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
    }

    @Test
    void updatesNickname() throws Exception {
        updateNickname("  새닉네임  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("USER200_2"))
                // 앞뒤 공백은 제거된다
                .andExpect(jsonPath("$.result.nickname").value("새닉네임"));

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(jsonPath("$.result.nickname").value("새닉네임"));
        assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname())
                .isEqualTo("새닉네임");
    }

    @Test
    void rejectsBlankNickname() throws Exception {
        updateNickname("   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
    }

    @Test
    void rejectsTooLongNickname() throws Exception {
        updateNickname("가".repeat(21))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
    }

    @Test
    void doesNotChangeEmailOrProvider() throws Exception {
        mockMvc.perform(patch(ME_URL)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "새닉네임", "email": "hacker@example.com", "provider": "KAKAO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.email").value("user@example.com"))
                .andExpect(jsonPath("$.result.provider").value("LOCAL"));
    }

    @Test
    void returnsNotFoundWhenAccountNoLongerExists() throws Exception {
        String tokenOfDeletedUser = jwtTokenProvider.issueAccessToken(999_999L, "USER").value();

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + tokenOfDeletedUser))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER404_1"));
    }

    private ResultActions updateNickname(String nickname) throws Exception {
        return mockMvc.perform(patch(ME_URL)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nickname": "%s"}
                        """.formatted(nickname)));
    }
}
