package com.example.resay.domain.auth.controller;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.infrastructure.google.GoogleIdTokenVerifier;
import com.example.resay.global.infrastructure.google.GoogleUserInfo;
import com.example.resay.global.infrastructure.kakao.KakaoClient;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse.KakaoAccount;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse.Profile;
import com.jayway.jsonpath.JsonPath;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 소셜 신규 회원: 로그인 → (온보딩) → POST /api/v1/auth/oauth/signup 으로 가입을 마친다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SocialSignupTest {

    private static final String SOCIAL_SIGNUP_URL = "/api/v1/auth/oauth/signup";
    private static final String ME_URL = "/api/v1/users/me";
    private static final String KAKAO_ID = "1234567890";
    private static final String GOOGLE_SUB = "110248495921238986420";
    private static final String REQUIRED_AGREEMENTS = """
            {"terms": true, "privacy": true, "voice": true}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Test
    void createsAccountWhenOnboardingIsFinished() throws Exception {
        String registerToken = registerTokenFromKakao("Kakao@Example.com");

        String body = signup(registerToken, "  온보딩닉네임 ", """
                {"terms": true, "privacy": true, "voice": true, "marketing": true}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("AUTH201_2"))
                .andExpect(jsonPath("$.result.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.result.refreshToken", notNullValue()))
                .andReturn().getResponse().getContentAsString();

        User user = userRepository.findByProviderAndProviderId(Provider.KAKAO, KAKAO_ID).orElseThrow();
        // 닉네임은 카카오 프로필이 아니라 온보딩에서 입력한 값
        assertThat(user.getNickname()).isEqualTo("온보딩닉네임");
        assertThat(user.getEmail()).isEqualTo("kakao@example.com");
        assertThat(user.getPassword()).isNull();
        assertThat(user.getTermsAgreedAt()).isNotNull();
        assertThat(user.getMarketingAgreedAt()).isEqualTo(user.getTermsAgreedAt());

        // 받은 토큰으로 바로 서비스를 쓸 수 있고, 다음 로그인부터는 기존 회원이다
        String accessToken = JsonPath.read(body, "$.result.accessToken");
        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.provider").value("KAKAO"));
        kakaoLogin().andExpect(jsonPath("$.result.isNewUser").value(false));
    }

    @Test
    void registerTokenCanBeUsedOnlyOnce() throws Exception {
        String registerToken = registerTokenFromKakao("kakao@example.com");
        signup(registerToken, "닉네임", REQUIRED_AGREEMENTS).andExpect(status().isCreated());

        signup(registerToken, "다른닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_4"));
        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void rejectsRegisterTokenThatWasNotIssued() throws Exception {
        signup("not-issued-token", "닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401_4"));
        assertThat(userRepository.count()).isZero();
    }

    // 잘못된 요청에 토큰을 써 버리면 사용자가 소셜 로그인부터 다시 해야 한다
    @Test
    void keepsRegisterTokenWhenRequestIsInvalid() throws Exception {
        String registerToken = registerTokenFromKakao("kakao@example.com");

        signup(registerToken, "닉네임", """
                {"terms": true, "privacy": true, "voice": false}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error['agreements.voice']", notNullValue()));
        signup(registerToken, "가".repeat(21), REQUIRED_AGREEMENTS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.nickname", notNullValue()));
        assertThat(userRepository.count()).isZero();

        signup(registerToken, "닉네임", REQUIRED_AGREEMENTS).andExpect(status().isCreated());
    }

    @Test
    void registerTokenIsNotAnAccessToken() throws Exception {
        String registerToken = registerTokenFromKakao("kakao@example.com");

        mockMvc.perform(get(ME_URL).header("Authorization", "Bearer " + registerToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void doesNotTakeOverEmailAlreadyUsedByAnotherAccount() throws Exception {
        userRepository.saveAndFlush(User.createLocal("kakao@example.com", "encoded-password", "기존회원"));

        signup(registerTokenFromKakao("kakao@example.com"), "닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isCreated());

        User kakaoUser = userRepository.findByProviderAndProviderId(Provider.KAKAO, KAKAO_ID).orElseThrow();
        assertThat(kakaoUser.getEmail()).isNull();
        assertThat(userRepository.count()).isEqualTo(2);
    }

    @Test
    void signsUpWhenUserDidNotShareEmail() throws Exception {
        signup(registerTokenFromKakao(null), "닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isCreated());

        User kakaoUser = userRepository.findByProviderAndProviderId(Provider.KAKAO, KAKAO_ID).orElseThrow();
        assertThat(kakaoUser.getEmail()).isNull();
        assertThat(kakaoUser.getMarketingAgreedAt()).isNull();
    }

    // 로그인을 두 번 해서 가입 토큰이 두 개인 경우: 계정은 하나만 생기고 처음 가입한 내용이 남는다
    @Test
    void secondRegisterTokenOfSameAccountLogsIntoFirstAccount() throws Exception {
        String first = registerTokenFromKakao("kakao@example.com");
        String second = registerTokenFromKakao("kakao@example.com");

        signup(first, "처음닉네임", REQUIRED_AGREEMENTS).andExpect(status().isCreated());
        signup(second, "나중닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.accessToken", notNullValue()));

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, KAKAO_ID).orElseThrow().getNickname())
                .isEqualTo("처음닉네임");
    }

    @Test
    void createsGoogleAccountFromGoogleRegisterToken() throws Exception {
        given(googleIdTokenVerifier.verify(any())).willReturn(new GoogleUserInfo(GOOGLE_SUB, "user@gmail.com", "홍길동"));
        String body = mockMvc.perform(post("/api/v1/auth/oauth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idToken": "google-id-token"}
                                """))
                .andReturn().getResponse().getContentAsString();

        signup(JsonPath.read(body, "$.result.registerToken"), "닉네임", REQUIRED_AGREEMENTS)
                .andExpect(status().isCreated());

        User googleUser = userRepository.findByProviderAndProviderId(Provider.GOOGLE, GOOGLE_SUB).orElseThrow();
        assertThat(googleUser.getEmail()).isEqualTo("user@gmail.com");
        assertThat(userRepository.findByProviderAndProviderId(Provider.KAKAO, GOOGLE_SUB)).isEmpty();
    }

    private String registerTokenFromKakao(String email) throws Exception {
        given(kakaoClient.fetchUser(any(), any())).willReturn(new KakaoUserResponse(
                Long.valueOf(KAKAO_ID),
                new KakaoAccount(email, new Profile("카카오닉네임"))
        ));
        String body = kakaoLogin()
                .andExpect(jsonPath("$.result.isNewUser").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.result.registerToken");
    }

    private ResultActions kakaoLogin() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/oauth/kakao")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"code": "auth-code", "redirectUri": "http://localhost:5173/oauth/kakao/callback"}
                        """));
    }

    private ResultActions signup(String registerToken, String nickname, String agreements) throws Exception {
        return mockMvc.perform(post(SOCIAL_SIGNUP_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"registerToken": "%s", "nickname": "%s", "agreements": %s}
                        """.formatted(registerToken, nickname, agreements)));
    }
}
