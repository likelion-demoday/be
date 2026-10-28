package com.example.resay.domain.auth.controller;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.google.GoogleIdTokenVerifier;
import com.example.resay.global.infrastructure.google.GoogleUserInfo;
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

/**
 * 토큰 검증 자체는 GoogleIdTokenVerifierTest에서 실제 서명으로 확인하고,
 * 여기서는 검증 결과에 따른 가입·로그인·응답을 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoogleLoginTest {

    private static final String GOOGLE_LOGIN_URL = "/api/v1/auth/oauth/google";
    private static final String GOOGLE_SUB = "110248495921238986420";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Test
    void firstLoginGivesRegisterTokenWithoutCreatingAccount() throws Exception {
        givenGoogleUser(GOOGLE_SUB, "user@gmail.com", "홍길동");

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200_4"))
                .andExpect(jsonPath("$.result.isNewUser").value(true))
                .andExpect(jsonPath("$.result.registerToken", notNullValue()))
                .andExpect(jsonPath("$.result.accessToken", nullValue()));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void existingUserGetsTokensAsBefore() throws Exception {
        userRepository.saveAndFlush(User.createSocial(Provider.GOOGLE, GOOGLE_SUB, "user@gmail.com", "홍길동"));
        givenGoogleUser(GOOGLE_SUB, "user@gmail.com", "홍길동");

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isNewUser").value(false))
                .andExpect(jsonPath("$.result.accessToken", notNullValue()))
                .andExpect(jsonPath("$.result.refreshToken", notNullValue()));
    }

    @Test
    void doesNotTreatKakaoAccountWithSameProviderIdAsGoogleAccount() throws Exception {
        userRepository.saveAndFlush(User.createSocial(Provider.KAKAO, GOOGLE_SUB, null, "카카오유저"));
        givenGoogleUser(GOOGLE_SUB, null, "구글유저");

        login()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isNewUser").value(true));
    }

    @Test
    void returnsUnauthorizedWhenIdTokenIsRejected() throws Exception {
        givenVerifierFails(AuthErrorCode.SOCIAL_LOGIN_FAILED);

        login()
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_3"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void returnsBadGatewayWhenGoogleIsUnavailable() throws Exception {
        givenVerifierFails(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);

        login()
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("AUTH502_1"));
    }

    @Test
    void returnsServiceUnavailableWhenNotConfigured() throws Exception {
        givenVerifierFails(AuthErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);

        login()
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH503_1"));
    }

    @Test
    void rejectsMissingIdToken() throws Exception {
        mockMvc.perform(post(GOOGLE_LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.idToken", notNullValue()));
    }

    private void givenGoogleUser(String sub, String email, String name) {
        given(googleIdTokenVerifier.verify(any())).willReturn(new GoogleUserInfo(sub, email, name));
    }

    private void givenVerifierFails(AuthErrorCode errorCode) {
        willThrow(new GeneralException(errorCode)).given(googleIdTokenVerifier).verify(any());
    }

    private ResultActions login() throws Exception {
        return mockMvc.perform(post(GOOGLE_LOGIN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"idToken": "google-id-token"}
                        """));
    }
}
