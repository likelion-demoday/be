package com.example.resay.global.infrastructure.kakao;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 카카오 키가 없는 팀원 로컬에서도 서버가 뜨도록 값을 필수로 두지 않는다.
 * 대신 카카오 로그인 API를 호출할 때 {@link #isConfigured()}로 확인한다.
 */
@ConfigurationProperties(prefix = "oauth.kakao")
public record KakaoProperties(
        String clientId,
        String clientSecret,
        List<String> allowedRedirectUris,
        String tokenUri,
        String userInfoUri
) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    public boolean isAllowedRedirectUri(String redirectUri) {
        return allowedRedirectUris != null && allowedRedirectUris.contains(redirectUri);
    }
}
