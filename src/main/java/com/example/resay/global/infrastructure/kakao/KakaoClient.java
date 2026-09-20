package com.example.resay.global.infrastructure.kakao;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.global.exception.GeneralException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 카카오 인가 코드를 토큰으로 바꾸고 사용자 정보를 조회한다.
 * 카카오 액세스 토큰은 사용자 정보 조회에만 쓰고 저장하거나 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class KakaoClient {

    private final KakaoProperties kakaoProperties;
    private final RestClient restClient;

    public KakaoClient(KakaoProperties kakaoProperties, RestClient kakaoRestClient) {
        this.kakaoProperties = kakaoProperties;
        this.restClient = kakaoRestClient;
    }

    public KakaoUserResponse fetchUser(String authorizationCode, String redirectUri) {
        String accessToken = exchangeToken(authorizationCode, redirectUri);
        return fetchUserInfo(accessToken);
    }

    private String exchangeToken(String authorizationCode, String redirectUri) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", kakaoProperties.clientId());
        form.add("redirect_uri", redirectUri);
        form.add("code", authorizationCode);
        if (kakaoProperties.clientSecret() != null && !kakaoProperties.clientSecret().isBlank()) {
            form.add("client_secret", kakaoProperties.clientSecret());
        }

        KakaoTokenResponse response = call(() -> restClient.post()
                .uri(kakaoProperties.tokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class));

        if (response == null || response.accessToken() == null) {
            throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
        }
        return response.accessToken();
    }

    private KakaoUserResponse fetchUserInfo(String accessToken) {
        KakaoUserResponse response = call(() -> restClient.get()
                .uri(kakaoProperties.userInfoUri())
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserResponse.class));

        if (response == null || response.id() == null) {
            throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
        }
        return response;
    }

    private <T> T call(KakaoCall<T> kakaoCall) {
        try {
            return kakaoCall.execute();
        } catch (RestClientResponseException exception) {
            // 인가 코드 만료·재사용, 잘못된 redirect_uri 등 요청 문제
            if (exception.getStatusCode().is4xxClientError()) {
                log.warn("카카오 로그인 실패: status={}", exception.getStatusCode());
                throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_FAILED);
            }
            log.error("카카오 서버 오류: status={}", exception.getStatusCode());
            throw new GeneralException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        } catch (ResourceAccessException exception) {
            // 연결 실패·타임아웃
            log.error("카카오 서버 통신 실패", exception);
            throw new GeneralException(AuthErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }
    }

    @FunctionalInterface
    private interface KakaoCall<T> {
        T execute();
    }
}
