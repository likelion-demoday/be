package com.example.resay.global.infrastructure.google;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 구글 Client ID가 없는 팀원 로컬에서도 서버가 뜨도록 값을 필수로 두지 않는다.
 * 설정되지 않은 상태에서 구글 로그인을 호출하면 503으로 응답한다.
 */
@ConfigurationProperties(prefix = "oauth.google")
public record GoogleProperties(
        String clientId,
        String jwkSetUri
) {

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }
}
