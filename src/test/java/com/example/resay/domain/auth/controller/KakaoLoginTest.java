package com.example.resay.domain.auth.controller;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.kakao.KakaoClient;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse.KakaoAccount;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class KakaoLoginTest {

    private static final String KAKAO_LOGIN_URL = "/api/v1/auth/oauth/kakao";
    private static final String REDIRECT_URI = "http://localhost:5173/oauth/kakao/callback";
    private static final String KAKAO_ID = "1234567890";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    @Test
    void firstLoginGivesRegisterTokenWithoutCreatingAccount() throws Exception {
        givenKakaoUser("kakao@example.com", "카카오닉네임");

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_4"))
                .andExpect(jsonPath("$.result.isNewUser").value(true))
                .andExpect(jsonPath("$.result.registerToken", notNullValue()))
                .andExpect(jsonPath("$.result.registerExpiresIn").value(1800))
                .andExpect(jsonPath("$.result.accessToken", nullValue()))
                .andExpect(jsonPath("$.result.refreshToken", nullValue()));

        // 약관에 동의하기 전에는 계정도 이메일도 저장하지 않는다
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void existingUserGetsTokensAsBefore() throws Exception {
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, KAKAO_ID, "kakao@example.com", "카카오닉네임"));
        givenKakaoUser("kakao@example.com", "카카오닉네임");

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isNewUser").value(false))
                .andExpect(jsonPath("$.result.registerToken", nullValue()))
                .andExpect(jsonPath("$.result.accessToken", notNullValue()))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.result.refreshToken", notNullValue()));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsRedirectUriThatIsNotAllowed() throws Exception {
        mockMvc.perform(post(KAKAO_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code": "auth-code", "redirectUri": "https://evil.example.com/callback"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH400_1"));
    }

    @Test
    void returnsUnauthorizedWhenKakaoRejectsAuthorizationCode() throws Exception {
        willThrow(new GeneralException(AuthErrorCode.SOCIAL_LOGIN_FAILED))
                .given(kakaoClient).fetchUser(any(), any());

        login()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_3"));
    }

    @Test
    void returnsBadGatewayWhenKakaoIsUnavailable() throws Exception {
        willThrow(new GeneralException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE))
                .given(kakaoClient).fetchUser(any(), any());

        login()
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH502_1"));
    }

    @Test
    void rejectsMissingAuthorizationCode() throws Exception {
        mockMvc.perform(post(KAKAO_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"redirectUri": "%s"}
                                """.formatted(REDIRECT_URI)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", notNullValue()));
    }

    private void givenKakaoUser(String email, String nickname) {
        given(kakaoClient.fetchUser(any(), any())).willReturn(new KakaoUserResponse(
                Long.valueOf(KAKAO_ID),
                new KakaoAccount(email, nickname == null ? null : new Profile(nickname))
        ));
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post(KAKAO_LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "auth-code", "redirectUri": "%s"}
                        """.formatted(REDIRECT_URI)));
    }
}
