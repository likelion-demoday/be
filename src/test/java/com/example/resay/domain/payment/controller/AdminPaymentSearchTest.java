package com.example.resay.domain.payment.controller;

import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.Role;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자가 환불할 주문을 이메일로 찾는 API
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminPaymentSearchTest {

    private static final String SEARCH_URL = "/api/v1/admin/payments/search";
    private static final String EMAIL = "refund-target@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long userId;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        userId = userRepository.saveAndFlush(User.createLocal(EMAIL, "encoded-password", "대상")).getId();
        Long adminId = userRepository.saveAndFlush(
                User.createLocal("search-admin@example.com", "encoded-password", "운영자")).getId();
        adminToken = jwtTokenProvider.issueAccessToken(adminId, Role.ADMIN.name()).value();
        userToken = jwtTokenProvider.issueAccessToken(userId, Role.USER.name()).value();
    }

    // 결제된 충전만 최근 것부터 나오고, 지금 잔액으로 전액 환불할 수 있는 주문에만 refundable이 붙는다
    @Test
    void findsPaidOrdersWithBalanceAndRefundability() throws Exception {
        Payment small = paidOrder("RS261013small", 1000);
        Payment refunded = paidOrder("RS261013refunded", 5000);
        Payment large = paidOrder("RS261013large", 3000);
        order("RS261013unpaid", 10000);
        // 5,000은 환불됐고, 남은 4,000 중 2,000을 썼다 → 잔액 2,000
        paymentRepository.markCancelling(refunded.getId(), LocalDateTime.now());
        paymentRepository.markCancelled(
                refunded.getId(), PaymentStatus.CANCELLING, LocalDateTime.now(), LocalDateTime.now());
        creditService.collectChargeRefund(userId, refunded.getId(), 1L, 5000);
        creditService.useForAnalysis(userId, 77L, AnalysisCategory.CONFLICT);

        search(adminToken, EMAIL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("PAYMENT200_6"))
                .andExpect(jsonPath("$.result.userId").value(userId))
                .andExpect(jsonPath("$.result.balance").value(2000))
                .andExpect(jsonPath("$.result.payments.length()").value(3))
                .andExpect(jsonPath("$.result.payments[0].orderId").value(large.getOrderId()))
                .andExpect(jsonPath("$.result.payments[0].status").value("PAID"))
                .andExpect(jsonPath("$.result.payments[0].credits").value(3000))
                // 잔액(2,000)이 충전 크레딧(3,000)보다 적다
                .andExpect(jsonPath("$.result.payments[0].refundable").value(false))
                .andExpect(jsonPath("$.result.payments[1].orderId").value(refunded.getOrderId()))
                .andExpect(jsonPath("$.result.payments[1].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.payments[1].cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.result.payments[1].refundable").value(false))
                .andExpect(jsonPath("$.result.payments[2].orderId").value(small.getOrderId()))
                .andExpect(jsonPath("$.result.payments[2].refundable").value(true));
    }

    @Test
    void returnsEmptyListForUserWithoutPayments() throws Exception {
        search(adminToken, "  " + EMAIL + " ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.balance").value(0))
                .andExpect(jsonPath("$.result.payments.length()").value(0));
    }

    @Test
    void returnsNotFoundForUnknownEmail() throws Exception {
        search(adminToken, "nobody@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER404_1"));
    }

    // 다른 사람의 결제 내역과 잔액을 일반 사용자가 볼 수 없어야 한다
    @Test
    void onlyAdminCanSearch() throws Exception {
        search(userToken, EMAIL)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403_1"));
        mockMvc.perform(post(SEARCH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + EMAIL + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    // 이메일은 본문으로만 받는다 (주소에 실리면 접속 로그에 남는다)
    @Test
    void requiresEmailInBody() throws Exception {
        search(adminToken, "").andExpect(status().isBadRequest());
        search(adminToken, "not-an-email").andExpect(status().isBadRequest());
        mockMvc.perform(get(SEARCH_URL).param("email", EMAIL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().is4xxClientError());
    }

    private ResultActions search(String token, String email) throws Exception {
        return mockMvc.perform(post(SEARCH_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private Payment order(String orderId, int amount) {
        return paymentRepository.saveAndFlush(
                Payment.create(userId, orderId, "CREDIT_" + amount, amount, amount, "http://localhost:3000"));
    }

    // 결제까지 끝나 크레딧이 지급된 주문
    private Payment paidOrder(String orderId, int amount) {
        Payment order = order(orderId, amount);
        LocalDateTime now = LocalDateTime.now();
        paymentRepository.markApproving(order.getId(), "TID" + orderId, "hash-" + orderId, now);
        paymentRepository.markPaid(order.getId(), now, null, now);
        creditService.charge(userId, order.getId(), amount);
        return paymentRepository.findById(order.getId()).orElseThrow();
    }
}
