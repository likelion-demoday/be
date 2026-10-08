package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 결제창이 보낸 인증 결과 → 승인 → 크레딧 지급까지의 흐름.
// 단계마다 따로 커밋되는 것을 확인해야 해서 테스트 트랜잭션으로 감싸지 않는다. 만든 데이터는 테스트마다 지운다.
// 나이스페이 API 호출만 목으로 바꾸고, 서명 검증은 실제 코드로 한다
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentApprovalFlowTest {

    private static final String RETURN_URL = "/api/v1/payments/nicepay/return";
    // src/test/resources/application.yml 의 값
    private static final String CLIENT_KEY = "S2_test-client-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String FRONT_ORIGIN = "http://localhost:3000";
    private static final String RECEIPT_URL = "https://npg.nicepay.co.kr/issue/IssueLoader.do?TID=test";
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 10, 8, 15, 30, 12);
    // 처리 도중 예상하지 못한 예외가 나면 주문 상태만으로는 드러나지 않는다 (예외를 삼키고 결과 화면으로 보내기 때문)
    private static final String UNEXPECTED_ERROR_LOG = "결제 인증 결과 처리 중 오류";
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentReconciler paymentReconciler;

    @Autowired
    private PaymentApprovalService paymentApprovalService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    @Autowired
    private CreditWalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private NicepayClient nicepayClient;

    @MockitoSpyBean
    private CreditService creditService;

    private Long userId;
    private boolean unexpectedErrorIsPartOfTest;

    @BeforeEach
    void setUp() {
        userId = userRepository.saveAndFlush(User.createLocal(
                "charge-flow-" + SEQUENCE.incrementAndGet() + "@example.com", "encoded-password", "충전")).getId();
    }

    @AfterEach
    void cleanUp(CapturedOutput output) {
        ledgerRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        if (!unexpectedErrorIsPartOfTest) {
            assertThat(output.getOut()).doesNotContain(UNEXPECTED_ERROR_LOG);
        }
    }

    @Test
    void approvesAndGrantsCreditsWhenAuthResultMatchesOrder() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(paid(order, tid));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));

        Payment paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.getTid()).isEqualTo(tid);
        assertThat(paid.getPaidAt()).isEqualTo(PAID_AT);
        assertThat(paid.getReceiptUrl()).isEqualTo(RECEIPT_URL);
        assertThat(paid.getApprovalRequestedAt()).isNotNull();
        // 인증 토큰은 저장하지 않고 지문만 남긴다
        assertThat(paid.getAuthTokenHash()).hasSize(64).doesNotContain(authToken(tid));
        assertThat(balance()).isEqualTo(3000);

        List<CreditLedgerEntry> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger).hasSize(1);
        assertThat(ledger.get(0).getType()).isEqualTo(CreditLedgerType.CHARGE);
        assertThat(ledger.get(0).getAmount()).isEqualTo(3000);
        assertThat(ledger.get(0).getBalanceAfter()).isEqualTo(3000);
        assertThat(ledger.get(0).getPaymentId()).isEqualTo(order.getId());
        verify(nicepayClient, never()).netCancel(anyString());
    }

    @Test
    void addsToExistingBalance() throws Exception {
        Payment first = order(1000);
        String firstTid = newTid();
        when(nicepayClient.approve(firstTid, 1000)).thenReturn(paid(first, firstTid));
        postAuthResult(first, firstTid).andExpect(status().isSeeOther());
        Payment second = order(10000);
        String secondTid = newTid();
        when(nicepayClient.approve(secondTid, 10000)).thenReturn(paid(second, secondTid));

        postAuthResult(second, secondTid).andExpect(status().isSeeOther());

        assertThat(balance()).isEqualTo(11000);
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(11000);
    }

    // 같은 인증 결과가 다시 전송돼도(연속 전송, 뒤로 가기 후 재전송) 승인도 지급도 한 번만 한다
    @Test
    void sameAuthResultTwiceIsApprovedAndGrantedOnce() throws Exception {
        Payment order = order(5000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 5000)).thenReturn(paid(order, tid));
        postAuthResult(order, tid).andExpect(status().isSeeOther());

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));

        verify(nicepayClient, times(1)).approve(anyString(), anyInt());
        assertThat(balance()).isEqualTo(5000);
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).hasSize(1);
    }

    // 카드 인증에 실패했거나 사용자가 취소한 경우 (모바일). 승인을 요청하지 않으므로 결제는 일어나지 않는다
    @Test
    void doesNotApproveWhenAuthenticationFailed() throws Exception {
        Payment order = order(3000);

        mockMvc.perform(form()
                        .param("authResultCode", "I002")
                        .param("authResultMsg", "사용자가 결제를 취소하였습니다.")
                        .param("clientId", CLIENT_KEY)
                        .param("orderId", order.getOrderId())
                        .param("amount", "3000"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("I002");
        assertThat(failed.getFailMessage()).isEqualTo("사용자가 결제를 취소하였습니다.");
        verifyNoInteractions(nicepayClient);
        assertThat(balance()).isZero();
    }

    // 인증 실패 통보는 서명이 없어 누가 보냈는지 알 수 없다. 코드는 나이스페이 형식(4자)일 때만 그대로 남기고,
    // 로그에 가짜 줄을 만들거나 우리가 붙이는 코드를 흉내 낸 값은 버린다
    @ParameterizedTest
    @ValueSource(strings = {"", "RESULT_MISMATCH", "NET_CANCELLED", "A1\r\n2026 ERROR 충전 완료: orderId=FAKE", "12345"})
    void doesNotKeepUntrustedFailCode(String authResultCode, CapturedOutput output) throws Exception {
        Payment order = order(3000);

        mockMvc.perform(form()
                        .param("authResultCode", authResultCode)
                        .param("authResultMsg", "가".repeat(500))
                        .param("orderId", order.getOrderId()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("AUTH_FAILED");
        assertThat(failed.getFailMessage()).hasSize(200);
        assertThat(output.getOut()).doesNotContain("orderId=FAKE");
        verifyNoInteractions(nicepayClient);
    }

    // 1,000원으로 인증받은 결과를 10,000원 주문에 보내는 경우. 서명은 1,000원 기준으로는 맞다
    @Test
    void rejectsAuthResultOfCheaperAmount() throws Exception {
        Payment order = order(10000);
        String tid = newTid();

        mockMvc.perform(form()
                        .param("authResultCode", "0000")
                        .param("tid", tid)
                        .param("clientId", CLIENT_KEY)
                        .param("orderId", order.getOrderId())
                        .param("amount", "1000")
                        .param("authToken", authToken(tid))
                        .param("signature", sign(authToken(tid) + CLIENT_KEY + "1000" + SECRET_KEY)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        assertRejectedWithoutApproval(order);
    }

    // 금액만 주문 금액으로 고쳐 보내면 서명이 맞지 않는다
    @Test
    void rejectsAuthResultWhenAmountWasAlteredAfterSigning() throws Exception {
        Payment order = order(10000);
        String tid = newTid();

        mockMvc.perform(form()
                        .param("authResultCode", "0000")
                        .param("tid", tid)
                        .param("clientId", CLIENT_KEY)
                        .param("orderId", order.getOrderId())
                        .param("amount", "10000")
                        .param("authToken", authToken(tid))
                        .param("signature", sign(authToken(tid) + CLIENT_KEY + "1000" + SECRET_KEY)))
                .andExpect(status().isSeeOther());

        assertRejectedWithoutApproval(order);
    }

    @ParameterizedTest
    @CsvSource({
            // 서명 없음, 엉뚱한 서명, 시크릿 키를 모르는 쪽이 만든 서명
            "S2_test-client-key, VALID_TID, ''",
            "S2_test-client-key, VALID_TID, 0000000000000000000000000000000000000000000000000000000000000000",
            "S2_test-client-key, VALID_TID, FORGED",
            // 다른 상점의 인증 결과
            "S2_other-client-key, VALID_TID, VALID",
            // tid는 승인 API 주소에 들어간다. 형식이 다르면 요청하지 않는다
            "S2_test-client-key, ../netcancel, VALID",
            "S2_test-client-key, '', VALID"
    })
    void rejectsAuthResultThatCannotBeTrusted(String clientId, String tid, String signature) throws Exception {
        Payment order = order(3000);
        String token = "NICETOKEN" + SEQUENCE.incrementAndGet();
        String signed = switch (signature) {
            case "VALID" -> sign(token + clientId + "3000" + SECRET_KEY);
            case "FORGED" -> sign(token + clientId + "3000" + "guessed-secret-key");
            default -> signature;
        };

        mockMvc.perform(form()
                        .param("authResultCode", "0000")
                        .param("tid", "VALID_TID".equals(tid) ? newTid() : tid)
                        .param("clientId", clientId)
                        .param("orderId", order.getOrderId())
                        .param("amount", "3000")
                        .param("authToken", token)
                        .param("signature", signed))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        assertRejectedWithoutApproval(order);
    }

    // 모르는 주문은 돌려보낼 프론트 주소도 알 수 없다
    @Test
    void rejectsAuthResultOfUnknownOrder() throws Exception {
        mockMvc.perform(form().param("authResultCode", "0000").param("orderId", "RS261008unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT400_3"));
        mockMvc.perform(form().param("authResultCode", "0000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT400_3"));

        verifyNoInteractions(nicepayClient);
    }

    // 한도 초과처럼 카드사가 거절한 경우. 실패로 끝내기 전에 조회로 결제되지 않았음을 한 번 확인한다
    @Test
    void failsOrderWhenApprovalIsDeclined() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(rejected("3095", "카드사 실패 응답"));
        when(nicepayClient.find(tid)).thenReturn(found(order, tid, "failed"));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("3095");
        assertThat(failed.getFailMessage()).isEqualTo("카드사 실패 응답");
        assertThat(failed.getTid()).isEqualTo(tid);
        assertThat(balance()).isZero();
        verify(nicepayClient, never()).netCancel(anyString());
    }

    // 승인 API가 오류로 답했는데 카드 승인은 난 경우. 조회에서 결제 완료가 확인되면 지급한다
    @Test
    void grantsWhenApprovalAnsweredErrorButLookupSaysPaid() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(rejected("A299", "API 지연처리 발생"));
        when(nicepayClient.find(tid)).thenReturn(paid(order, tid));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 승인은 거절로 답했는데 확인용 조회의 답을 받지 못하면 단정하지 않는다
    @Test
    void keepsApprovingWhenLookupAfterRejectionGivesNoAnswer() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(rejected("X003", "전문 수신 중 오류"));
        when(nicepayClient.find(tid)).thenThrow(new NicepayUnknownResultException("timeout", null));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(balance()).isZero();
    }

    // 승인 응답을 받지 못하면 결제됐을 수도 있다. 망취소를 요청하고, 취소가 확인되면 실패로 끝낸다
    @Test
    void netCancelsWhenApprovalResponseIsLost() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenReturn(found(order, tid, "cancelled"));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("NET_CANCELLED");
        assertThat(balance()).isZero();
        verify(nicepayClient).netCancel(order.getOrderId());
    }

    // 망취소가 확인되지 않으면 실패로 단정하지 않는다. 정리 작업이 조회해서 결제됐으면 크레딧을 지급한다
    @Test
    void keepsApprovingWhenNetCancelIsNotConfirmedThenReconcilerGrantsIfPaid() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenReturn(notFound());

        // 아직 결과를 모르므로 성공 화면으로 보내고, 화면이 상태를 다시 조회한다
        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(balance()).isZero();

        when(nicepayClient.find(tid)).thenReturn(paid(order, tid));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 망취소 요청이 성공이라고 답해도 거래가 취소 상태가 아니면 취소됐다고 보지 않는다
    @Test
    void keepsApprovingWhenNetCancelSucceedsWithoutCancelledStatus() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenReturn(found(order, tid, "paid"));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
    }

    @Test
    void reconcilerFailsStuckOrderWhenNicepayHasNoPayment() throws Exception {
        Payment order = stuckOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(notFound());

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("NOT_PAID");
        assertThat(balance()).isZero();
    }

    // 인증만 하고 승인되지 않았거나, 승인 뒤 취소된 거래
    @ParameterizedTest
    @ValueSource(strings = {"ready", "failed", "cancelled", "expired"})
    void reconcilerFailsStuckOrderWhenNicepayStatusIsNotPaid(String nicepayStatus) throws Exception {
        Payment order = stuckOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(found(order, order.getTid(), nicepayStatus));

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance()).isZero();
    }

    // 조회 응답을 못 받았거나, 조회가 거절됐거나(키 오류), 처음 보는 상태면 결제 여부를 모른다. 그대로 두고 다음에 다시 본다
    @Test
    void reconcilerLeavesStuckOrderWhenLookupCannotTell() throws Exception {
        Payment order = stuckOrder(3000);
        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);

        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(new NicepayTransaction(
                401, "U116", "사용자 정보가 존재하지 않습니다.", null, null, null, 0, null, null, Signature.MISSING));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);

        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(found(order, order.getTid(), "partialCancelled"));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);

        // 나중에 조회가 되면 그때 맞춘다
        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 방금 승인을 요청해 아직 처리 중일 수 있는 주문은 정리 작업이 건드리지 않는다
    @Test
    void reconcilerDoesNotTouchOrderThatJustStartedApproval() throws Exception {
        Payment order = stuckOrder(3000);

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(2));

        verify(nicepayClient, never()).find(anyString());
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
    }

    // 한 주문에서 예외가 나도 나머지 주문은 정리한다
    @Test
    void reconcilerContinuesWithNextOrderWhenOneFails() throws Exception {
        Payment broken = stuckOrder(3000);
        Payment healthy = stuckOrder(1000);
        when(nicepayClient.find(broken.getTid())).thenThrow(new IllegalStateException("예상하지 못한 오류"));
        when(nicepayClient.find(healthy.getTid())).thenReturn(paid(healthy, healthy.getTid()));

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        assertThat(reload(broken).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(reload(healthy).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(1000);
    }

    // 나이스페이가 응답하지 않으면 건마다 타임아웃까지 기다리지 않고 이번 실행을 멈춘다 (다른 정리 작업이 밀리지 않게)
    @Test
    void reconcilerStopsEarlyWhenNicepayDoesNotAnswer() throws Exception {
        stuckOrder(1000);
        stuckOrder(1000);
        stuckOrder(1000);
        when(nicepayClient.find(anyString())).thenThrow(new NicepayUnknownResultException("timeout", null));

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        verify(nicepayClient, times(2)).find(anyString());
        assertThat(paymentRepository.findAll()).allMatch(payment -> payment.getStatus() == PaymentStatus.APPROVING);
    }

    // 1시간이 지나도 확정되지 않은 주문은 사람이 봐야 하므로 오류 로그로 드러낸다
    @Test
    void reconcilerReportsOrdersThatStayUnsettledTooLong(CapturedOutput output) throws Exception {
        Payment order = stuckOrder(3000);
        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(30));
        assertThat(output.getOut()).doesNotContain("1시간 넘게 확인되지 않은");

        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(61));
        assertThat(output.getOut()).contains("1시간 넘게 확인되지 않은 충전 주문이 1건");
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
    }

    // 다른 주문의 인증 결과를 끼워 넣은 경우: 결제는 됐지만 이 주문의 결제가 아니다. 크레딧을 주지 않고 방금 승인한 그 거래를 취소한다
    @ParameterizedTest
    @CsvSource({"ORDER_ID", "AMOUNT", "TID"})
    void cancelsCapturedPaymentWhenPaidResultIsNotForThisOrder(String mismatch) throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        String paidOrderId = "ORDER_ID".equals(mismatch) ? "RS261008anotherorder" : order.getOrderId();
        when(nicepayClient.approve(tid, 3000)).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.",
                "TID".equals(mismatch) ? newTid() : tid,
                paidOrderId,
                "paid",
                "AMOUNT".equals(mismatch) ? 1000 : 3000,
                PAID_AT, RECEIPT_URL, Signature.VALID));
        when(nicepayClient.cancel(eq(tid), anyString(), anyString())).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, paidOrderId, "cancelled", 3000, PAID_AT, null, Signature.VALID));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("RESULT_MISMATCH");
        assertThat(failed.getFailMessage()).contains("취소했습니다");
        assertThat(balance()).isZero();
        assertThat(ledgerRepository.count()).isZero();
        // 응답의 주문번호는 믿을 수 없으므로 주문번호로 찾는 망취소를 쓰지 않는다 (이미 지급한 다른 결제를 취소할 수 있다)
        verify(nicepayClient).cancel(eq(tid), anyString(), anyString());
        verify(nicepayClient, never()).netCancel(anyString());
    }

    // 취소가 확인되지 않아도 크레딧은 주지 않는다. 사람이 취소해야 하므로 오류 로그를 남긴다
    @Test
    void doesNotGrantEvenIfCancelOfMismatchedPaymentIsNotConfirmed(CapturedOutput output) throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, "RS261008anotherorder", "paid", 3000,
                PAID_AT, RECEIPT_URL, Signature.VALID));
        when(nicepayClient.cancel(eq(tid), anyString(), anyString()))
                .thenThrow(new NicepayUnknownResultException("timeout", null));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        Payment failed = reload(order);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(failed.getFailCode()).isEqualTo("RESULT_MISMATCH");
        assertThat(balance()).isZero();
        assertThat(output.getOut()).contains("나이스페이 관리자에서 취소 필요");
    }

    // 값은 주문과 맞는데 서명이 다르면 지급도 실패 처리도 하지 않는다. 조회 응답의 서명이 맞으면 그때 지급한다
    @Test
    void holdsPaymentWhenResponseSignatureIsWrong() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), "paid", 3000,
                PAID_AT, RECEIPT_URL, Signature.INVALID));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(balance()).isZero();
        verify(nicepayClient, never()).netCancel(anyString());
        verify(nicepayClient, never()).cancel(anyString(), anyString(), anyString());

        when(nicepayClient.find(tid)).thenReturn(paid(order, tid));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 서명은 응답의 필수 항목이 아니다. 없더라도 주문번호 · tid · 금액이 맞으면 결제를 확정한다
    @Test
    void grantsWhenResponseHasNoSignatureButMatchesOrder() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), "paid", 3000,
                null, null, Signature.MISSING));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        Payment paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        // 결제 시각이 응답에 없으면 확정한 시각을 쓴다
        assertThat(paid.getPaidAt()).isNotNull();
        assertThat(balance()).isEqualTo(3000);
    }

    // 이미 결제에 쓴 인증 결과를 같은 금액의 새 주문에 다시 보내는 경우 (서명은 맞다)
    @Test
    void rejectsAuthResultAlreadyUsedByAnotherOrder() throws Exception {
        Payment first = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(paid(first, tid));
        postAuthResult(first, tid).andExpect(status().isSeeOther());
        Payment second = order(3000);

        postAuthResult(second, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + second.getOrderId()));

        verify(nicepayClient, times(1)).approve(anyString(), anyInt());
        Payment rejected = reload(second);
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(rejected.getFailCode()).isEqualTo("INVALID_AUTH_RESULT");
        assertThat(rejected.getTid()).isNull();
        assertThat(balance()).isEqualTo(3000);
    }

    // 인증 결과의 서명은 tid를 덮지 않는다. 서명이 맞는 인증 결과 하나에 tid만 바꿔 여러 주문으로 승인을 시도하는 것을 막는다
    @Test
    void rejectsSameAuthTokenWithDifferentTid() throws Exception {
        Payment first = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(rejected("U121", "인증 요청내역이 존재하지 않습니다."));
        when(nicepayClient.find(tid)).thenReturn(notFound());
        postAuthResult(first, tid).andExpect(status().isSeeOther());
        Payment second = order(3000);
        String guessedTid = newTid();

        mockMvc.perform(form()
                        .param("authResultCode", "0000")
                        .param("tid", guessedTid)
                        .param("clientId", CLIENT_KEY)
                        .param("orderId", second.getOrderId())
                        .param("amount", "3000")
                        // 첫 주문에 쓴 인증 토큰 · 서명을 그대로 다시 보낸다
                        .param("authToken", authToken(tid))
                        .param("signature", sign(authToken(tid) + CLIENT_KEY + "3000" + SECRET_KEY)))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + second.getOrderId()));

        verify(nicepayClient, never()).approve(guessedTid, 3000);
        Payment rejected = reload(second);
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(rejected.getFailCode()).isEqualTo("INVALID_AUTH_RESULT");
        assertThat(rejected.getTid()).isNull();
    }

    // 요청은 성공인데 결제 완료가 아닌 응답은 단정하지 않고 정리 작업에 넘긴다
    @Test
    void keepsApprovingWhenApprovalSucceedsWithoutPaidStatus() throws Exception {
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(found(order, tid, "ready"));

        postAuthResult(order, tid).andExpect(status().isSeeOther());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(balance()).isZero();
    }

    // 결제는 됐는데 크레딧 지급이 실패하면 "결제 완료"로 바꾼 것도 함께 취소돼야 한다.
    // 주문이 PAID로 남으면 정리 작업이 다시 보지 않아 크레딧 없는 결제가 된다
    @Test
    void keepsOrderApprovingWhenCreditGrantFailsThenGrantsLater() throws Exception {
        unexpectedErrorIsPartOfTest = true;
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenReturn(paid(order, tid));
        doThrow(new IllegalStateException("지갑 잠금 실패")).when(creditService).charge(anyLong(), anyLong(), anyInt());

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));

        Payment held = reload(order);
        assertThat(held.getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(held.getPaidAt()).isNull();
        assertThat(balance()).isZero();
        verify(nicepayClient, never()).netCancel(anyString());

        doCallRealMethod().when(creditService).charge(anyLong(), anyLong(), anyInt());
        when(nicepayClient.find(tid)).thenReturn(paid(order, tid));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 승인 호출이 예상하지 못한 예외로 끝나면 결제 여부를 모른다. 실패로 바꾸지 않고 정리 작업에 넘긴다
    @Test
    void keepsApprovingWhenApprovalThrowsUnexpectedly() throws Exception {
        unexpectedErrorIsPartOfTest = true;
        Payment order = order(3000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 3000)).thenThrow(new IllegalStateException("예상하지 못한 오류"));

        postAuthResult(order, tid)
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/success?orderId=" + order.getOrderId()));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.APPROVING);

        when(nicepayClient.find(tid)).thenReturn(paid(order, tid));
        paymentReconciler.settleStuckApprovals(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 이미 "결제되지 않음"으로 끝난 주문에 결제 완료 응답이 오면, 크레딧을 줄 수 없으므로 그 결제를 취소한다
    @Test
    void cancelsPaymentThatArrivesAfterOrderWasClosed() throws Exception {
        Payment stuck = stuckOrder(3000);
        paymentRepository.markFailed(stuck.getId(), PaymentStatus.APPROVING, "NOT_PAID", null, LocalDateTime.now());
        when(nicepayClient.find(stuck.getTid())).thenReturn(paid(stuck, stuck.getTid()));

        paymentApprovalService.settleStuckApproval(stuck);

        assertThat(reload(stuck).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance()).isZero();
        verify(nicepayClient).cancel(eq(stuck.getTid()), anyString(), anyString());
    }

    // 결제창을 닫아 결제하지 않은 주문은 30분 뒤 만료되고, 그 뒤에 도착한 인증 결과는 승인하지 않는다
    @Test
    void expiresUnpaidOrderAndIgnoresLateAuthResult() throws Exception {
        Payment order = order(3000);

        assertThat(paymentReconciler.expireAbandonedOrders(LocalDateTime.now().plusMinutes(29))).isZero();
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.READY);
        assertThat(paymentReconciler.expireAbandonedOrders(LocalDateTime.now().plusMinutes(31))).isEqualTo(1);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.EXPIRED);

        postAuthResult(order, newTid())
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        FRONT_ORIGIN + "/payments/fail?orderId=" + order.getOrderId()));

        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.EXPIRED);
    }

    // 만료 처리는 결제가 진행 중이거나 끝난 주문을 건드리지 않는다
    @Test
    void expirationDoesNotTouchOrdersInProgressOrFinished() throws Exception {
        Payment approving = stuckOrder(3000);
        Payment paidOrder = order(1000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 1000)).thenReturn(paid(paidOrder, tid));
        postAuthResult(paidOrder, tid).andExpect(status().isSeeOther());

        assertThat(paymentReconciler.expireAbandonedOrders(LocalDateTime.now().plusDays(1))).isZero();

        assertThat(reload(approving).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(reload(paidOrder).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // 인증 결과는 로그인 정보 없이 온다. 출처는 PC에서는 프론트 주소, 모바일에서는 나이스페이 · 카드사 주소나 null이다.
    // 출처 검사(CORS)나 로그인 검사에 걸리면 결제를 끝낼 수 없다
    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000", "https://pay.nicepay.co.kr", "https://ansimclick.hyundaicard.com", "null"})
    void acceptsAuthResultFromAnyOriginWithoutLogin(String origin) throws Exception {
        Payment order = order(1000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 1000)).thenReturn(paid(order, tid));

        mockMvc.perform(authResult(order, tid)
                        .header(HttpHeaders.ORIGIN, origin)
                        // 프론트가 모든 요청에 붙이는 토큰이 만료된 채 따라와도 막히지 않는다
                        .header("Authorization", "Bearer expired-or-garbage-token"))
                .andExpect(status().isSeeOther());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // 출처 검사를 푼 것은 인증 결과 주소 하나뿐이다
    @Test
    void otherPaymentApisStillRejectUnknownOrigins() throws Exception {
        mockMvc.perform(post("/api/v1/payments")
                        .header(HttpHeaders.ORIGIN, "https://pay.nicepay.co.kr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productCode\": \"CREDIT_1000\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RETURN_URL + "/extra")
                        .header(HttpHeaders.ORIGIN, "https://pay.nicepay.co.kr")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(status().isForbidden());
    }

    private void assertRejectedWithoutApproval(Payment order) {
        Payment rejected = reload(order);
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(rejected.getFailCode()).isEqualTo("INVALID_AUTH_RESULT");
        assertThat(rejected.getTid()).isNull();
        verifyNoInteractions(nicepayClient);
        assertThat(balance()).isZero();
    }

    private ResultActions postAuthResult(Payment order, String tid) throws Exception {
        return mockMvc.perform(authResult(order, tid).header(HttpHeaders.ORIGIN, "https://pay.nicepay.co.kr"));
    }

    // 결제창이 인증에 성공했을 때 보내는 값. 서명은 나이스페이와 같은 공식으로 테스트에서 직접 만든다
    private MockHttpServletRequestBuilder authResult(Payment order, String tid) {
        return form()
                .param("authResultCode", "0000")
                .param("authResultMsg", "인증 성공")
                .param("tid", tid)
                .param("clientId", CLIENT_KEY)
                .param("orderId", order.getOrderId())
                .param("amount", String.valueOf(order.getAmount()))
                .param("mallReserved", "")
                .param("authToken", authToken(tid))
                .param("signature", sign(authToken(tid) + CLIENT_KEY + order.getAmount() + SECRET_KEY));
    }

    private MockHttpServletRequestBuilder form() {
        return post(RETURN_URL).contentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    private Payment order(int amount) {
        return paymentRepository.saveAndFlush(Payment.create(
                userId, newOrderId(), "CREDIT_" + amount, amount, amount, FRONT_ORIGIN));
    }

    // 승인을 요청했지만 결과를 받지 못해 APPROVING으로 남은 주문
    private Payment stuckOrder(int amount) throws Exception {
        Payment order = order(amount);
        String tid = newTid();
        when(nicepayClient.approve(tid, amount)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenThrow(new NicepayUnknownResultException("timeout", null));
        postAuthResult(order, tid).andExpect(status().isSeeOther());
        reset(nicepayClient);
        Payment stuck = reload(order);
        assertThat(stuck.getStatus()).isEqualTo(PaymentStatus.APPROVING);
        return stuck;
    }

    private NicepayTransaction paid(Payment order, String tid) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), "paid", order.getAmount(),
                PAID_AT, RECEIPT_URL, Signature.VALID);
    }

    // 요청은 성공(0000)이고 거래 상태만 다른 응답
    private NicepayTransaction found(Payment order, String tid, String nicepayStatus) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), nicepayStatus, order.getAmount(),
                null, null, Signature.VALID);
    }

    private NicepayTransaction rejected(String resultCode, String resultMsg) {
        return new NicepayTransaction(200, resultCode, resultMsg, "", "", "", 0, null, null, Signature.MISSING);
    }

    private NicepayTransaction notFound() {
        return new NicepayTransaction(
                404, "U107", "거래내역이 존재 하지 않습니다.", "", "", "", 0, null, null, Signature.MISSING);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private int balance() {
        return creditQueryService.getSummary(userId).balance();
    }

    private static String newOrderId() {
        return "RS261008" + String.format("%032x", SEQUENCE.incrementAndGet());
    }

    private static String newTid() {
        return "UT0000113m0101261008" + String.format("%010d", SEQUENCE.incrementAndGet());
    }

    // 인증 결과마다 토큰이 다르다 (같은 토큰은 한 주문에만 쓸 수 있다)
    private static String authToken(String tid) {
        return "NICETOKEN" + tid;
    }

    private static String sign(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
