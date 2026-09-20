package com.example.resay.global.infrastructure.kakao;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class KakaoClientTest {

    private static final String TOKEN_URI = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer kakaoServer;
    private KakaoClient kakaoClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        kakaoServer = MockRestServiceServer.bindTo(builder).build();
        KakaoProperties properties = new KakaoProperties(
                "client-id",
                "client-secret",
                List.of("http://localhost:5173/oauth/kakao/callback"),
                TOKEN_URI,
                USER_INFO_URI
        );
        kakaoClient = new KakaoClient(properties, builder.build());
    }

    @Test
    void exchangesAuthorizationCodeAndReturnsUser() {
        kakaoServer.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("grant_type=authorization_code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=auth-code")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_id=client-id")))
                .andRespond(withSuccess("""
                        {"access_token": "kakao-access-token"}
                        """, MediaType.APPLICATION_JSON));
        kakaoServer.expect(requestTo(USER_INFO_URI))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer kakao-access-token"))
                .andRespond(withSuccess("""
                        {
                          "id": 1234567890,
                          "kakao_account": {
                            "email": "kakao@example.com",
                            "profile": {"nickname": "카카오닉네임"}
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        KakaoUserResponse user = kakaoClient.fetchUser("auth-code", "http://localhost:5173/oauth/kakao/callback");

        assertThat(user.id()).isEqualTo(1234567890L);
        assertThat(user.email()).isEqualTo("kakao@example.com");
        assertThat(user.nickname()).isEqualTo("카카오닉네임");
        kakaoServer.verify();
    }

    @Test
    void returnsNullsWhenUserDidNotAgreeToShareEmailAndNickname() {
        kakaoServer.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("""
                        {"access_token": "kakao-access-token"}
                        """, MediaType.APPLICATION_JSON));
        kakaoServer.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("""
                        {"id": 42, "kakao_account": {}}
                        """, MediaType.APPLICATION_JSON));

        KakaoUserResponse user = kakaoClient.fetchUser("auth-code", "http://localhost:5173/oauth/kakao/callback");

        assertThat(user.id()).isEqualTo(42L);
        assertThat(user.email()).isNull();
        assertThat(user.nickname()).isNull();
    }

    @Test
    void failsWhenAuthorizationCodeIsRejected() {
        kakaoServer.expect(requestTo(TOKEN_URI))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
                        .body("""
                                {"error": "invalid_grant"}
                                """)
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoClient.fetchUser("used-code", "http://localhost:5173/oauth/kakao/callback"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_LOGIN_FAILED));
    }

    @Test
    void reportsProviderErrorWhenKakaoServerFails() {
        kakaoServer.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(() -> kakaoClient.fetchUser("auth-code", "http://localhost:5173/oauth/kakao/callback"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));
    }
}
