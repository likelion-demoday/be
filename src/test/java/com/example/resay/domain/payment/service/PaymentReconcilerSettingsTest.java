package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.config.PaymentProperties;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentReconcilerSettingsTest {

    // 승인 요청이 아직 진행 중인 주문을 정리 작업이 "결제되지 않음"으로 끝내면, 그 직후 도착한 결제 완료 응답이 갈 곳이 없다.
    // 대기 시간이 승인 + 망취소에 걸릴 수 있는 시간보다 짧게 설정되면 서버가 뜨지 않아야 한다
    @Test
    void refusesToStartWhenGracePeriodIsShorterThanApprovalCanTake() {
        assertThatThrownBy(() -> reconciler(Duration.ofSeconds(60), Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval-grace-period");
        assertThatThrownBy(() -> reconciler(Duration.ofMinutes(3), Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startsWithDefaultSettings() {
        assertThatCode(() -> reconciler(Duration.ofMinutes(3), Duration.ofSeconds(5), Duration.ofSeconds(30)))
                .doesNotThrowAnyException();
    }

    private static PaymentReconciler reconciler(Duration gracePeriod, Duration connectTimeout, Duration readTimeout) {
        PaymentProperties paymentProperties = new PaymentProperties(
                List.of(new PaymentProperties.Product("CREDIT_1000", 1000, 1000)),
                Duration.ofMinutes(30), gracePeriod, false);
        NicepayProperties nicepayProperties = new NicepayProperties(
                "S2_client-key", "secret-key", "", "http://localhost:8080/api/v1/payments/nicepay/return",
                connectTimeout, readTimeout);
        return new PaymentReconciler(
                mock(PaymentRepository.class), mock(PaymentApprovalService.class), paymentProperties, nicepayProperties);
    }
}
