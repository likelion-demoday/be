package com.example.resay.global.infrastructure.nicepay;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 나이스페이 키가 없는 팀원 로컬에서도 서버가 뜨도록 값을 필수로 두지 않는다.
 * 대신 충전 주문을 만들 때 {@link #isConfigured()}로 확인한다.
 *
 * @param clientKey  결제창과 API 인증에 쓰는 상점 키. 브라우저에 그대로 내려가는 값이다
 * @param secretKey  API 인증과 서명 검증에 쓰는 키. 백엔드 밖으로 내보내지 않는다
 * @param apiBaseUrl 비워 두면 키 종류에 맞는 주소를 쓴다 ({@link #resolvedApiBaseUrl()})
 * @param returnUrl  결제창이 인증 결과를 보내는 백엔드 주소
 */
@ConfigurationProperties(prefix = "nicepay")
public record NicepayProperties(
        String clientKey,
        String secretKey,
        String apiBaseUrl,
        String returnUrl,
        Duration connectTimeout,
        Duration readTimeout
) {

    static final String SANDBOX_API_BASE_URL = "https://sandbox-api.nicepay.co.kr";
    static final String PRODUCTION_API_BASE_URL = "https://api.nicepay.co.kr";
    // 클라이언트 키의 접두어는 나이스페이 결제창 스크립트가 서버를 고르는 기준이다 (스크립트 소스에서 확인).
    //   첫 글자: R = 운영 상점, S = 테스트 상점(샌드박스)
    //   숫자:    2 = Server 승인(우리가 승인 API를 부른다), 1 = Client 승인(결제창이 바로 승인한다)
    private static final String PRODUCTION_KEY_PREFIX = "R";
    private static final String SERVER_APPROVAL_KEY_PATTERN = "[RS]2_.+";

    public boolean isConfigured() {
        return hasText(clientKey) && hasText(secretKey) && hasText(returnUrl);
    }

    /** 충전 주문을 받아도 되는 설정인지: 키가 있고, 그 키가 Server 승인 방식이어야 한다. */
    public boolean canAcceptPayments() {
        return isConfigured() && isServerApprovalKey();
    }

    /**
     * 테스트 상점 키는 샌드박스에서만, 운영 상점 키는 운영 주소에서만 통한다.
     * 주소를 따로 지정하지 않으면 키에 맞춰 고르므로, 운영 전환 때 키만 바꾸면 된다.
     * 운영 키로 확인되지 않는 키는 실제 결제가 일어나지 않는 샌드박스로 보낸다.
     */
    public String resolvedApiBaseUrl() {
        if (hasText(apiBaseUrl)) {
            return apiBaseUrl;
        }
        return isProductionKey() ? PRODUCTION_API_BASE_URL : SANDBOX_API_BASE_URL;
    }

    public boolean isProductionKey() {
        return hasText(clientKey) && clientKey.startsWith(PRODUCTION_KEY_PREFIX);
    }

    /**
     * Server 승인 방식으로 발급한 키인지. Client 승인 키(R1_ · S1_)로는 결제창이 인증과 동시에 결제해 버려서
     * 이 서버의 승인 흐름(주문 확인 → 승인 → 크레딧 지급)이 성립하지 않는다 (돈만 나가고 크레딧이 지급되지 않는다).
     */
    public boolean isServerApprovalKey() {
        return hasText(clientKey) && clientKey.matches(SERVER_APPROVAL_KEY_PATTERN);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
