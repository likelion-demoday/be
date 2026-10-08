package com.example.resay.global.infrastructure.nicepay;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NicepayPropertiesTest {

    private static final String RETURN_URL = "https://api.resay.site/api/v1/payments/nicepay/return";

    // 운영 전환 때 키만 바꾸면 되도록, 주소를 지정하지 않으면 키 종류에 맞는 주소를 고른다
    @Test
    void picksApiHostByKeyTypeWhenBaseUrlIsNotSet() {
        assertThat(properties("S2_0123456789abcdef", "secret", "").resolvedApiBaseUrl())
                .isEqualTo("https://sandbox-api.nicepay.co.kr");
        // 예전 테스트 상점 키는 접두어가 없다
        assertThat(properties("58e3b578555e45738d6b569e53d5ae54", "secret", " ").resolvedApiBaseUrl())
                .isEqualTo("https://sandbox-api.nicepay.co.kr");
        assertThat(properties("R2_0123456789abcdef", "secret", null).resolvedApiBaseUrl())
                .isEqualTo("https://api.nicepay.co.kr");
        assertThat(properties("R1_0123456789abcdef", "secret", "").resolvedApiBaseUrl())
                .isEqualTo("https://api.nicepay.co.kr");
    }

    // 이 서버의 흐름은 Server 승인 키(숫자 2)에서만 성립한다
    @Test
    void recognizesServerApprovalKeys() {
        assertThat(properties("S2_0123456789abcdef", "secret", "").isServerApprovalKey()).isTrue();
        assertThat(properties("R2_0123456789abcdef", "secret", "").isServerApprovalKey()).isTrue();
        assertThat(properties("S1_0123456789abcdef", "secret", "").isServerApprovalKey()).isFalse();
        assertThat(properties("R1_0123456789abcdef", "secret", "").isServerApprovalKey()).isFalse();
        assertThat(properties("58e3b578555e45738d6b569e53d5ae54", "secret", "").isServerApprovalKey()).isFalse();
    }

    // Client 승인 키로는 돈만 나가고 크레딧이 지급되지 않으므로, 키가 있어도 주문을 받지 않는다
    @Test
    void acceptsPaymentsOnlyWithServerApprovalKeyAndSecret() {
        assertThat(properties("S2_0123456789abcdef", "secret", "").canAcceptPayments()).isTrue();
        assertThat(properties("R2_0123456789abcdef", "secret", "").canAcceptPayments()).isTrue();
        assertThat(properties("R1_0123456789abcdef", "secret", "").canAcceptPayments()).isFalse();
        assertThat(properties("S1_0123456789abcdef", "secret", "").canAcceptPayments()).isFalse();
        assertThat(properties("R2_0123456789abcdef", "", "").canAcceptPayments()).isFalse();
        assertThat(properties(null, null, "").canAcceptPayments()).isFalse();
    }

    @Test
    void usesExplicitBaseUrlWhenSet() {
        assertThat(properties("R2_0123456789abcdef", "secret", "http://localhost:9999").resolvedApiBaseUrl())
                .isEqualTo("http://localhost:9999");
    }

    @Test
    void isNotConfiguredWithoutBothKeys() {
        assertThat(properties("S2_0123456789abcdef", "secret", "").isConfigured()).isTrue();
        assertThat(properties("", "secret", "").isConfigured()).isFalse();
        assertThat(properties("S2_0123456789abcdef", " ", "").isConfigured()).isFalse();
        assertThat(properties(null, null, "").isConfigured()).isFalse();
    }

    private static NicepayProperties properties(String clientKey, String secretKey, String apiBaseUrl) {
        return new NicepayProperties(
                clientKey, secretKey, apiBaseUrl, RETURN_URL, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
