package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.repository.PaymentCancelRepository;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentCancelServiceTest {

    // 나이스페이 키가 없는 서버에서는 취소 요청을 보낼 수 없다.
    // 크레딧만 회수한 채 "확인 중"으로 멈추지 않도록, 아무것도 바꾸기 전에 거절한다
    @Test
    void refusesBeforeCollectingCreditsWhenNicepayIsNotConfigured() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentCancelRepository paymentCancelRepository = mock(PaymentCancelRepository.class);
        NicepayClient nicepayClient = mock(NicepayClient.class);
        CreditService creditService = mock(CreditService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        NicepayProperties notConfigured = new NicepayProperties(
                "", "", "", "http://localhost:8080/api/v1/payments/nicepay/return",
                Duration.ofSeconds(5), Duration.ofSeconds(30));
        Payment order = NicepayFixtures.newOrder(1L, 3000);
        when(paymentRepository.findByOrderId(order.getOrderId())).thenReturn(Optional.of(order));
        PaymentCancelService service = new PaymentCancelService(
                paymentRepository, paymentCancelRepository, nicepayClient, notConfigured, creditService,
                transactionTemplate);

        assertThatThrownBy(() -> service.cancel(order.getOrderId(), "환불", 99L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_UNAVAILABLE));

        verifyNoInteractions(transactionTemplate, creditService, nicepayClient, paymentCancelRepository);
    }
}
