package com.example.resay.domain.payment.controller;

import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerTest {

    private static final String PAYMENTS_URL = "/api/v1/payments";
    private static final String PRODUCTS_URL = "/api/v1/payments/products";
    // src/test/resources/application.yml 의 cors.allowed-origins
    private static final String FRONT_ORIGIN = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private User user;
    private String accessToken;

    @BeforeEach
    void setUp() {
        user = userRepository.saveAndFlush(User.createLocal("charge-api@example.com", "encoded-password", "충전"));
        accessToken = jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()).value();
    }

    @Test
    void requiresLogin() throws Exception {
        mockMvc.perform(get(PRODUCTS_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401_1"));
        mockMvc.perform(post(PAYMENTS_URL)
                        .header(HttpHeaders.ORIGIN, FRONT_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"CREDIT_1000\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(PAYMENTS_URL)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PAYMENTS_URL + "/RS000000")).andExpect(status().isUnauthorized());

        assertThat(paymentRepository.count()).isZero();
    }

    @Test
    void returnsChargeProducts() throws Exception {
        mockMvc.perform(get(PRODUCTS_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT200_1"))
                .andExpect(jsonPath("$.result.products", hasSize(4)))
                .andExpect(jsonPath("$.result.products[0].code").value("CREDIT_1000"))
                .andExpect(jsonPath("$.result.products[0].amount").value(1000))
                .andExpect(jsonPath("$.result.products[0].credits").value(1000))
                .andExpect(jsonPath("$.result.products[3].code").value("CREDIT_10000"))
                .andExpect(jsonPath("$.result.products[3].amount").value(10000));
    }

    @ParameterizedTest
    @CsvSource({
            "CREDIT_1000, 1000, 'Resay 크레딧 1,000'",
            "CREDIT_3000, 3000, 'Resay 크레딧 3,000'",
            "CREDIT_5000, 5000, 'Resay 크레딧 5,000'",
            "CREDIT_10000, 10000, 'Resay 크레딧 10,000'"
    })
    void createsOrderWithServerDecidedAmount(String productCode, int amount, String goodsName) throws Exception {
        createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"" + productCode + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT200_2"))
                // 날짜 6자리 + 추측할 수 없는 32자리
                .andExpect(jsonPath("$.result.orderId", matchesPattern("RS\\d{6}[0-9a-f]{32}")))
                .andExpect(jsonPath("$.result.amount").value(amount))
                .andExpect(jsonPath("$.result.goodsName").value(goodsName))
                .andExpect(jsonPath("$.result.clientId").value("S2_test-client-key"))
                .andExpect(jsonPath("$.result.method").value("card"))
                .andExpect(jsonPath("$.result.returnUrl")
                        .value("http://localhost:8080/api/v1/payments/nicepay/return"));

        Payment payment = paymentRepository.findAll().get(0);
        assertThat(payment.getUserId()).isEqualTo(user.getId());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(payment.getProductCode()).isEqualTo(productCode);
        assertThat(payment.getAmount()).isEqualTo(amount);
        assertThat(payment.getCreditAmount()).isEqualTo(amount);
        assertThat(payment.getResultOrigin()).isEqualTo(FRONT_ORIGIN);
        assertThat(payment.getTid()).isNull();
    }

    // 시크릿 키는 어떤 응답에도 나가지 않는다
    @Test
    void neverExposesSecretKey() throws Exception {
        String body = createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"CREDIT_1000\"}")
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("test-secret-key");
    }

    // 프론트가 금액을 보내도 쓰지 않는다
    @Test
    void ignoresAmountSentByClient() throws Exception {
        createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"CREDIT_10000\", \"amount\": 1, \"credits\": 999999}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.amount").value(10000));

        Payment payment = paymentRepository.findAll().get(0);
        assertThat(payment.getAmount()).isEqualTo(10000);
        assertThat(payment.getCreditAmount()).isEqualTo(10000);
    }

    @Test
    void rejectsUnknownProduct() throws Exception {
        createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"CREDIT_1\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT400_1"));
        createOrder(accessToken, FRONT_ORIGIN, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"))
                .andExpect(jsonPath("$.error.productCode").exists());

        assertThat(paymentRepository.count()).isZero();
    }

    // 결제 후 돌아갈 주소를 정할 수 없으면 주문을 만들지 않는다
    @Test
    void rejectsRequestWithoutOrigin() throws Exception {
        createOrder(accessToken, null, "{\"productCode\": \"CREDIT_1000\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT400_2"));

        assertThat(paymentRepository.count()).isZero();
    }

    // 허용 목록에 없는 사이트에서는 주문을 만들 수 없다 (결제 후 아무 사이트로나 보내는 통로가 되지 않게)
    @Test
    void rejectsRequestFromUnknownOrigin() throws Exception {
        createOrder(accessToken, "https://evil.example.com", "{\"productCode\": \"CREDIT_1000\"}")
                .andExpect(status().isForbidden());

        assertThat(paymentRepository.count()).isZero();
    }

    // 한 사용자가 짧은 시간에 주문을 무한정 만들지 못한다. 응답은 다른 횟수 제한과 같은 형식이다 (남은 시간을 알려준다)
    @Test
    void limitsOrdersPerUser() throws Exception {
        for (int count = 0; count < 20; count++) {
            createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"CREDIT_1000\"}").andExpect(status().isOk());
        }
        // 주문이 끝났는지와 상관없이 만든 횟수로 센다 (만든 주문을 곧바로 실패시키며 계속 만드는 것을 막는다)
        Payment first = paymentRepository.findAll().get(0);
        paymentRepository.markFailed(first.getId(), PaymentStatus.READY, "AUTH_FAILED", null, LocalDateTime.now());

        createOrder(accessToken, FRONT_ORIGIN, "{\"productCode\": \"CREDIT_1000\"}")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON429_1"))
                .andExpect(jsonPath("$.error.retryAfterSeconds").isNumber())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        assertThat(paymentRepository.count()).isEqualTo(20);
    }

    @Test
    void returnsOwnPayment() throws Exception {
        String orderId = orderOf(user.getId(), "CREDIT_3000", 3000);

        mockMvc.perform(get(PAYMENTS_URL + "/" + orderId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT200_3"))
                .andExpect(jsonPath("$.result.orderId").value(orderId))
                .andExpect(jsonPath("$.result.productCode").value("CREDIT_3000"))
                .andExpect(jsonPath("$.result.amount").value(3000))
                .andExpect(jsonPath("$.result.credits").value(3000))
                .andExpect(jsonPath("$.result.status").value("READY"))
                .andExpect(jsonPath("$.result.paidAt").doesNotExist())
                .andExpect(jsonPath("$.result.failMessage").doesNotExist());
    }

    // 남의 주문은 있는지조차 알려주지 않는다
    @Test
    void hidesPaymentOfAnotherUser() throws Exception {
        User other = userRepository.saveAndFlush(User.createLocal("other-payer@example.com", "encoded-password", "남"));
        String orderId = orderOf(other.getId(), "CREDIT_1000", 1000);

        mockMvc.perform(get(PAYMENTS_URL + "/" + orderId).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT404_1"));
        mockMvc.perform(get(PAYMENTS_URL + "/RS000000unknown").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT404_1"));
    }

    // 내역에는 결제된 주문만 나온다
    @Test
    void historyShowsOnlyPaidPayments() throws Exception {
        orderOf(user.getId(), "CREDIT_1000", 1000);
        String paidOrderId = orderOf(user.getId(), "CREDIT_5000", 5000);
        Payment paid = paymentRepository.findByOrderId(paidOrderId).orElseThrow();
        LocalDateTime now = LocalDateTime.now();
        paymentRepository.markApproving(paid.getId(), "UT0000113m01012610081530120001", "a".repeat(64), now);
        paymentRepository.markPaid(paid.getId(), now, "https://npg.nicepay.co.kr/receipt", now);
        User other = userRepository.saveAndFlush(User.createLocal("other-history@example.com", "encoded-password", "남"));
        orderOf(other.getId(), "CREDIT_3000", 3000);

        mockMvc.perform(get(PAYMENTS_URL).header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT200_4"))
                .andExpect(jsonPath("$.result.items", hasSize(1)))
                .andExpect(jsonPath("$.result.items[0].orderId").value(paidOrderId))
                .andExpect(jsonPath("$.result.items[0].status").value("PAID"))
                .andExpect(jsonPath("$.result.items[0].amount").value(5000))
                .andExpect(jsonPath("$.result.items[0].receiptUrl").value("https://npg.nicepay.co.kr/receipt"))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    private ResultActions createOrder(String token, String origin, String body) throws Exception {
        var request = post(PAYMENTS_URL)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (origin != null) {
            request.header(HttpHeaders.ORIGIN, origin);
        }
        return mockMvc.perform(request);
    }

    private String orderOf(Long userId, String productCode, int amount) {
        String orderId = "RS261008" + String.format("%032x", System.nanoTime());
        paymentRepository.saveAndFlush(Payment.create(userId, orderId, productCode, amount, amount, FRONT_ORIGIN));
        return orderId;
    }
}
