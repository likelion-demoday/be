package com.example.resay.domain.payment.service;

import com.example.resay.domain.payment.code.PaymentErrorCode;
import com.example.resay.domain.payment.config.PaymentProperties;
import com.example.resay.domain.payment.dto.PaymentOrderResponseDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.config.CorsProperties;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.exception.RateLimitExceededException;
import com.example.resay.global.infrastructure.nicepay.NicepayProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 설정에 따라 달라지는 주문 생성 규칙. (API 응답 형식과 DB 저장은 PaymentControllerTest가 확인한다)
class PaymentOrderServiceTest {

    private static final String FRONT_ORIGIN = "https://resay.site";
    private static final String RETURN_URL = "https://api.resay.site/api/v1/payments/nicepay/return";
    private static final Long USER_ID = 1L;

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);

    // 나이스페이 키가 없는 서버(키를 받지 않은 팀원 로컬 등)에서는 결제할 수 없는 주문을 만들지 않는다
    @Test
    void rejectsOrderWhenNicepayIsNotConfigured() {
        PaymentOrderService service = service(nicepay("S2_client-key", ""), false, FRONT_ORIGIN);

        assertUnavailable(service);
    }

    // Client 승인 키(숫자 1)로는 결제창이 인증과 동시에 결제해 버린다. 돈만 나가고 크레딧이 지급되지 않으므로 주문을 받지 않는다
    @ParameterizedTest
    @ValueSource(strings = {"R1_0123456789abcdef", "S1_0123456789abcdef", "0123456789abcdef0123456789abcdef"})
    void rejectsOrderWhenKeyIsNotForServerApproval(String clientKey) {
        PaymentOrderService service = service(nicepay(clientKey, "secret-key"), false, FRONT_ORIGIN);

        assertUnavailable(service);
    }

    // 결과 화면 주소는 허용 목록에 있는 프론트 주소여야 한다 (결제 후 비슷한 주소 · 아무 주소로 보내는 통로가 되지 않게)
    @ParameterizedTest
    @ValueSource(strings = {
            "https://resay.site.evil.example.com", "http://resay.site", "https://evil.example.com/resay.site",
            "https://api.resay.site", "null", "", " "})
    void rejectsOriginThatIsNotFrontend(String origin) {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), false, FRONT_ORIGIN);

        assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1000", origin))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_ORIGIN));
        verifyNoInteractions(paymentRepository);
    }

    // 허용 목록을 "*"로 열어 둔 서버에서는 아무 주소나 결과 화면이 될 수 있으므로 주문을 받지 않는다
    @Test
    void rejectsEveryOriginWhenAllowlistIsWildcard() {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), false, "*");

        assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1000", "https://evil.example.com"))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.INVALID_ORIGIN));
        verifyNoInteractions(paymentRepository);
    }

    // CORS 필터와 같은 규칙으로 비교한다. 허용 목록에 끝의 '/'나 대문자가 섞여 있어도 다른 API처럼 통과해야 한다
    @Test
    void acceptsFrontOriginEvenIfAllowlistIsWrittenLoosely() {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), false, "https://Resay.site/");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentOrderResponseDto order = service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN);

        assertThat(order.amount()).isEqualTo(1000);
        assertThat(order.returnUrl()).isEqualTo(RETURN_URL);
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        // 결제 후 돌려보낼 주소는 브라우저가 보낸 값 그대로다
        assertThat(saved.getValue().getResultOrigin()).isEqualTo(FRONT_ORIGIN);
    }

    // 테스트 상점 키로 한 결제는 돈이 나가지 않는데 크레딧은 지급된다. 공개된 서버에서는 운영자만 할 수 있다
    @Test
    void allowsOnlyAdminToPayWithTestKeyWhenRestricted() {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), true, FRONT_ORIGIN);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User admin = User.createLocal("admin@example.com", "encoded-password", "운영자");
        ReflectionTestUtils.setField(admin, "role", com.example.resay.domain.user.entity.Role.ADMIN);
        when(userRepository.findById(USER_ID))
                .thenReturn(Optional.of(User.createLocal("user@example.com", "encoded-password", "사용자")));
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.TEST_PAYMENT_NOT_ALLOWED));
        assertThat(service.create(2L, "CREDIT_1000", FRONT_ORIGIN).amount()).isEqualTo(1000);
    }

    // 운영 상점 키를 넣으면 같은 설정에서도 모든 사용자가 충전할 수 있다 (실제 돈이 나가는 결제다)
    @Test
    void allowsEveryoneToPayWithProductionKey() {
        PaymentOrderService service = service(nicepay("R2_client-key", "secret-key"), true, FRONT_ORIGIN);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN).clientId()).isEqualTo("R2_client-key");
        verifyNoInteractions(userRepository);
    }

    // 주문은 결제하지 않아도 행으로 남고 주문마다 승인 요청을 보낼 수 있다. 한 사용자가 짧은 시간에 무한정 만들지 못한다
    @Test
    void limitsOrdersPerUser() {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), false, FRONT_ORIGIN);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (int count = 0; count < 20; count++) {
            service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN);
        }

        assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception ->
                        assertThat(exception.getRetryAfterSeconds()).isBetween(1L, 600L));
        // 다른 사용자는 영향받지 않는다
        assertThat(service.create(2L, "CREDIT_1000", FRONT_ORIGIN).amount()).isEqualTo(1000);
    }

    // 잘못된 요청은 횟수에 넣지 않는다
    @Test
    void doesNotCountRejectedRequestsTowardLimit() {
        PaymentOrderService service = service(nicepay("S2_client-key", "secret-key"), false, FRONT_ORIGIN);
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        for (int count = 0; count < 30; count++) {
            assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1", FRONT_ORIGIN))
                    .isInstanceOf(GeneralException.class);
        }

        assertThat(service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN).amount()).isEqualTo(1000);
    }

    private void assertUnavailable(PaymentOrderService service) {
        assertThatThrownBy(() -> service.create(USER_ID, "CREDIT_1000", FRONT_ORIGIN))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(PaymentErrorCode.PAYMENT_UNAVAILABLE));
        verifyNoInteractions(paymentRepository);
    }

    private PaymentOrderService service(NicepayProperties nicepayProperties, boolean sandboxAdminOnly, String allowedOrigin) {
        PaymentProperties paymentProperties = new PaymentProperties(
                List.of(new PaymentProperties.Product("CREDIT_1000", 1000, 1000)),
                Duration.ofMinutes(30), Duration.ofMinutes(3), sandboxAdminOnly);
        return new PaymentOrderService(
                paymentRepository, paymentProperties, nicepayProperties,
                new CorsProperties(List.of(allowedOrigin)), userRepository);
    }

    private static NicepayProperties nicepay(String clientKey, String secretKey) {
        return new NicepayProperties(
                clientKey, secretKey, "", RETURN_URL, Duration.ofSeconds(5), Duration.ofSeconds(30));
    }
}
