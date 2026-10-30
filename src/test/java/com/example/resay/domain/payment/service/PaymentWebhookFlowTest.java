package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.dto.NicepayReturnRequestDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentCancelRepository;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.example.resay.domain.payment.service.NicepayFixtures.CANCELLED_AT;
import static com.example.resay.domain.payment.service.NicepayFixtures.SECRET_KEY;
import static com.example.resay.domain.payment.service.NicepayFixtures.cancelled;
import static com.example.resay.domain.payment.service.NicepayFixtures.found;
import static com.example.resay.domain.payment.service.NicepayFixtures.paid;
import static com.example.resay.domain.payment.service.NicepayFixtures.sign;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 나이스페이가 보내는 결제 · 취소 통보(웹훅)의 처리.
// 통보는 로그인 없이 누구나 보낼 수 있으므로 내용을 그대로 반영하지 않는지, 조회한 결과로만 주문을 바꾸는지를 확인한다.
// 나이스페이 API 호출만 목으로 바꾸고, 서명 검증은 실제 코드로 한다
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentWebhookFlowTest {

    private static final String WEBHOOK_URL = "/api/v1/payments/nicepay/webhook";
    private static final String EDI_DATE = "2026-10-12T16:00:05.512+0900";
    private static final Long ADMIN_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentApprovalService paymentApprovalService;

    @Autowired
    private PaymentCancelService paymentCancelService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentCancelRepository paymentCancelRepository;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    @Autowired
    private CreditUsageRepository usageRepository;

    @Autowired
    private CreditWalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private NicepayClient nicepayClient;

    private Long userId;

    @BeforeEach
    void setUp() {
        userId = userRepository.saveAndFlush(User.createLocal(
                "webhook-" + NicepayFixtures.nextSequence() + "@example.com", "encoded-password", "통보")).getId();
    }

    @AfterEach
    void cleanUp(CapturedOutput output) {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentCancelRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        assertThat(output.getOut()).doesNotContain("Unhandled exception");
        // 서명과 시크릿 키는 로그에 남기지 않는다
        assertThat(output.getOut()).doesNotContain(SECRET_KEY);
    }

    // 나이스페이는 HTTP 200에 본문이 "OK"(text/html)일 때만 통보가 전달된 것으로 기록한다
    @Test
    void answersOkInTheFormatNicepayExpects() throws Exception {
        Payment order = paidOrder(3000);

        sendNotification(order, "paid")
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("OK"));
        // 보내는 쪽이 JSON 응답을 받겠다고 해도 형식은 같다 (따옴표가 붙은 "OK"나 406이 되면 실패로 기록된다)
        mockMvc.perform(post(WEBHOOK_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(notification(order, "paid")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("OK"));
    }

    // 결제 완료 통보로는 아무것도 바꾸지 않는다. 결제 확정은 승인 흐름과 정리 작업만 한다.
    // 승인 요청이 진행 중일 때 통보를 보고 먼저 확정하면, 응답을 받지 못한 승인 요청이 보낸 망취소와 어긋나 크레딧만 남을 수 있다
    @Test
    void paidNotificationDoesNotConfirmPayment() throws Exception {
        Payment paid = paidOrder(3000);
        Payment approving = stuckOrder(3000);

        sendNotification(paid, "paid").andExpect(status().isOk()).andExpect(content().string("OK"));
        sendNotification(approving, "paid").andExpect(status().isOk()).andExpect(content().string("OK"));

        verifyNoInteractions(nicepayClient);
        assertThat(reload(paid).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(reload(approving).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(balance()).isEqualTo(3000);
    }

    // 나이스페이 관리자 화면에서 직접 취소한 결제: 통보를 받고 조회해 보니 취소돼 있다. 환불로 바꾸고 크레딧을 회수한다
    @Test
    void reflectsCancelMadeOutsideOurApi() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        Payment refunded = reload(order);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refunded.getCancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(balance()).isZero();

        PaymentCancel record = onlyCancelOf(order);
        assertThat(record.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        assertThat(record.getAmount()).isEqualTo(3000);
        assertThat(record.getCreditAmount()).isEqualTo(3000);
        // 우리가 요청한 취소가 아니다
        assertThat(record.getRequestedBy()).isNull();
        assertThat(record.getCancelOrderId()).isNull();
        assertThat(record.getCancelledAt()).isEqualTo(CANCELLED_AT);

        List<CreditLedgerEntry> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger).extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.CHARGE, CreditLedgerType.CHARGE_REFUND);
        assertThat(ledger.get(1).getAmount()).isEqualTo(-3000);
        // 우리 쪽에서 취소를 또 요청하지 않는다
        verify(nicepayClient, times(0)).cancel(anyString(), anyString(), anyString());
    }

    // 같은 통보가 여러 번 와도(재전송) 크레딧은 한 번만 회수한다
    @Test
    void collectsOnceWhenCancelNotificationIsRepeated() throws Exception {
        Payment order = paidOrder(3000);
        paidOrder(5000);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "cancelled").andExpect(status().isOk());
        sendNotification(order, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        // 다른 충전의 5,000은 그대로다
        assertThat(balance()).isEqualTo(5000);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId())).hasSize(1);
        // 이미 환불로 바뀐 주문은 다시 조회하지 않는다
        verify(nicepayClient, times(1)).find(anyString());
        assertLedgerMatchesBalance();
    }

    // 돈은 이미 돌아갔는데 사용자가 그 크레딧을 일부 써 버린 경우. 남은 만큼만 회수하고(잔액은 0 아래로 내려가지 않는다)
    // 모자란 만큼은 사람이 처리하도록 오류 로그로 남긴다
    @Test
    void collectsOnlyRemainingCreditsWhenSomeWereUsed(CapturedOutput output) throws Exception {
        Payment order = paidOrder(3000);
        creditService.useForAnalysis(userId, 77L, AnalysisCategory.CONFLICT);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "cancelled").andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance()).isZero();
        assertThat(onlyCancelOf(order).getCreditAmount()).isEqualTo(1000);
        assertThat(onlyCancelOf(order).getAmount()).isEqualTo(3000);
        assertThat(output.getOut()).contains("크레딧을 전부 회수하지 못했습니다").contains("부족=2000");
        assertLedgerMatchesBalance();
    }

    @Test
    void recordsCancelEvenWhenNothingIsLeftToCollect() throws Exception {
        Payment order = paidOrder(3000);
        creditService.adjust(userId, -3000, "전부 사용한 상황", ADMIN_ID);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "cancelled").andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(onlyCancelOf(order).getCreditAmount()).isZero();
        assertThat(balance()).isZero();
        // 회수한 것이 없으면 장부에 줄을 만들지 않는다
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.CHARGE, CreditLedgerType.ADJUST);
        assertLedgerMatchesBalance();
    }

    // 통보의 상태 값은 서명이 덮지 않는다. "취소됨"이라고 와도 조회 결과가 결제 완료면 아무것도 바꾸지 않는다
    @Test
    void doesNotTrustNotifiedStatus() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));

        sendNotification(order, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
        assertThat(paymentCancelRepository.findAll()).isEmpty();
    }

    // 서명이 맞지 않거나 없는 통보는 조회조차 하지 않는다 (아무나 이 주소로 나이스페이 조회를 일으킬 수 없게)
    @ParameterizedTest
    @ValueSource(strings = {"forged", "missing", "signedWithOtherAmount", "noAmount", "noEdiDate"})
    void ignoresNotificationItCannotVerify(String kind) throws Exception {
        Payment order = paidOrder(3000);
        String validSignature = sign(order.getTid() + 3000 + EDI_DATE + SECRET_KEY);
        String body = switch (kind) {
            case "forged" -> notification(order.getTid(), "cancelled", "3000", EDI_DATE, "0".repeat(64));
            case "missing" -> notification(order.getTid(), "cancelled", "3000", EDI_DATE, null);
            // 1,000원짜리 다른 거래의 통보에서 금액만 바꿔 온 경우
            case "signedWithOtherAmount" -> notification(order.getTid(), "cancelled", "3000", EDI_DATE,
                    sign(order.getTid() + 1000 + EDI_DATE + SECRET_KEY));
            case "noAmount" -> notification(order.getTid(), "cancelled", null, EDI_DATE, validSignature);
            default -> notification(order.getTid(), "cancelled", "3000", null, validSignature);
        };

        send(body).andExpect(status().isOk()).andExpect(content().string("OK"));

        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 일부만 취소된 결제는 자동으로 반영하지 않는다 (부분 취소는 아직 다루지 않는다. 사람이 크레딧을 조정한다)
    @Test
    void doesNotReflectPartialCancel() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(found(order, "partialCancelled"));

        sendNotification(order, "partialCancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 우리가 요청한 취소의 응답을 받지 못한 주문: 통보가 오면 정리 작업을 기다리지 않고 바로 확정한다. 크레딧은 이미 회수했다
    @Test
    void completesOurPendingCancel() throws Exception {
        Payment order = cancellingOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        PaymentCancel record = onlyCancelOf(order);
        assertThat(record.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        assertThat(record.getRequestedBy()).isEqualTo(ADMIN_ID);
        assertThat(balance()).isZero();
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.CHARGE, CreditLedgerType.CHARGE_REFUND);
    }

    // 취소 요청이 아직 진행 중일 수 있다. 통보를 받고 조회한 결과가 "결제 완료"여도 여기서는 크레딧을 되돌리지 않는다
    // (되돌린 직후 취소가 처리되면 돈도 크레딧도 돌려준 셈이 된다). 되돌리는 것은 충분히 기다린 뒤 정리 작업이 한다
    @Test
    void doesNotRevertPendingCancelFromNotification() throws Exception {
        Payment order = cancellingOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));

        sendNotification(order, "cancelled").andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLING);
        assertThat(onlyCancelOf(order).getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
        assertThat(balance()).isZero();
    }

    // 조회에 답이 없으면 실패로 답한다 (나이스페이가 실패로 기록해 관리자 화면에서 다시 보낼 수 있다)
    @Test
    void answersFailureWhenLookupIsUnanswered() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));

        sendNotification(order, "cancelled")
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("FAIL"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 결제되지 않고 끝난 주문 · 이미 환불된 주문의 취소 통보는 조회할 것도 없다
    @Test
    void ignoresCancelNotificationForOrdersWithNothingToRefund() throws Exception {
        Payment approving = stuckOrder(3000);
        Payment refunded = paidOrder(3000);
        when(nicepayClient.find(refunded.getTid())).thenReturn(cancelled(refunded));
        sendNotification(refunded, "cancelled").andExpect(status().isOk());
        reset(nicepayClient);

        sendNotification(approving, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));
        sendNotification(refunded, "cancelled").andExpect(status().isOk()).andExpect(content().string("OK"));

        verifyNoInteractions(nicepayClient);
        assertThat(reload(approving).getStatus()).isEqualTo(PaymentStatus.APPROVING);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(refunded.getId())).hasSize(1);
    }

    // 결제되지 않은 것으로 끝낸 주문에 뒤늦게 결제가 잡혔다 (정리 작업이 실패로 끝낸 뒤에 승인이 처리된 경우).
    // 끝난 주문에는 크레딧을 주지 않으므로 그 결제를 취소해 돈을 돌려준다
    @Test
    void refundsPaymentThatLandedAfterOrderFailed(CapturedOutput output) throws Exception {
        Payment order = failedOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(cancelled(order));

        sendNotification(order, "paid").andExpect(status().isOk()).andExpect(content().string("OK"));

        verify(nicepayClient, times(1)).cancel(eq(order.getTid()), anyString(), anyString());
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance()).isZero();
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).isEmpty();
        assertThat(output.getOut()).contains("이미 끝난 주문에 결제 완료 응답이 왔습니다");
    }

    // 실패로 끝낸 주문의 결제 통보가 늦게 도착했을 뿐, 조회해 보면 결제돼 있지 않다 (망취소된 거래 등). 취소를 요청하지 않는다
    @Test
    void doesNotCancelWhenFailedOrderIsNotPaidAtNicepay() throws Exception {
        Payment order = failedOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        sendNotification(order, "paid").andExpect(status().isOk()).andExpect(content().string("OK"));

        verify(nicepayClient, times(0)).cancel(anyString(), anyString(), anyString());
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(balance()).isZero();
    }

    // 우리 주문이 아닌 거래의 통보 (나이스페이 관리자 화면의 테스트 호출 등)에도 "OK"로 답한다
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"tid\":\"UT0000113m01012610120000000000\",\"status\":\"paid\",\"amount\":1004}",
            "{\"tid\":\"../../v1/payments\",\"status\":\"cancelled\",\"amount\":1004}",
            "{\"status\":\"paid\"}",
            "{}"
    })
    void answersOkToNotificationOfUnknownTransaction(String body) throws Exception {
        send(body).andExpect(status().isOk()).andExpect(content().string("OK"));

        verifyNoInteractions(nicepayClient);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not json", "[1,2]", "null", "{\"tid\":"})
    void rejectsUnreadableBody(String body) throws Exception {
        send(body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAYMENT400_4"));

        verifyNoInteractions(nicepayClient);
    }

    // 로그인 없이 받는 주소다. 엉뚱한 토큰이 붙어 와도 막히지 않는다
    @Test
    void doesNotRequireLogin() throws Exception {
        Payment order = paidOrder(3000);

        mockMvc.perform(post(WEBHOOK_URL)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-token")
                        .contentType("application/json;charset=utf-8")
                        .content(notification(order, "paid")))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    // 통보에 실려 온 값(상태 등)을 로그에 그대로 옮기지 않는다
    @Test
    void doesNotCopyUntrustedValuesIntoLog(CapturedOutput output) throws Exception {
        Payment order = paidOrder(3000);
        String status = "paid\\r\\n2026-10-12 ERROR 충전 환불 완료: orderId=FAKE";

        String signature = sign(order.getTid() + 3000 + EDI_DATE + SECRET_KEY);
        send(notification(order.getTid(), status, "3000", EDI_DATE, signature)).andExpect(status().isOk());

        assertThat(output.getOut()).doesNotContain("orderId=FAKE");
    }

    private ResultActions sendNotification(Payment order, String status) throws Exception {
        return send(notification(order, status));
    }

    private ResultActions send(String body) throws Exception {
        return mockMvc.perform(post(WEBHOOK_URL)
                .contentType("application/json;charset=utf-8")
                .content(body));
    }

    // 서명이 맞는 통보
    private String notification(Payment order, String status) {
        return notification(order.getTid(), status, String.valueOf(order.getAmount()), EDI_DATE,
                sign(order.getTid() + order.getAmount() + EDI_DATE + SECRET_KEY));
    }

    // 나이스페이가 보내는 형식. 쓰지 않는 항목(카드 · 구매자 정보 등)이 함께 온다
    private String notification(String tid, String status, String amount, String ediDate, String signature) {
        StringBuilder json = new StringBuilder("{\"resultCode\":\"0000\",\"resultMsg\":\"정상 처리되었습니다.\"");
        json.append(",\"tid\":\"").append(tid).append('"');
        json.append(",\"orderId\":\"RS261012webhook\",\"status\":\"").append(status).append('"');
        if (amount != null) {
            json.append(",\"amount\":").append(amount);
        }
        if (ediDate != null) {
            json.append(",\"ediDate\":\"").append(ediDate).append('"');
        }
        if (signature != null) {
            json.append(",\"signature\":\"").append(signature).append('"');
        }
        json.append(",\"paidAt\":\"2026-10-12T15:30:12.000+0900\",\"cancelledAt\":\"0\",\"payMethod\":\"card\"");
        json.append(",\"balanceAmt\":0,\"goodsName\":\"Resay 크레딧 3,000\",\"useEscrow\":false,\"currency\":\"KRW\"");
        json.append(",\"buyerName\":\"홍길동\",\"card\":{\"cardCode\":\"04\",\"cardNum\":\"536112******1234\"}");
        json.append(",\"cancels\":[{\"tid\":\"").append(tid).append("\",\"amount\":3000,\"reason\":\"테스트\"}]}");
        return json.toString();
    }

    private Payment order(int amount) {
        return paymentRepository.saveAndFlush(NicepayFixtures.newOrder(userId, amount));
    }

    // 결제까지 끝나 크레딧이 지급된 주문
    private Payment paidOrder(int amount) {
        Payment order = order(amount);
        String tid = NicepayFixtures.newTid();
        when(nicepayClient.approve(tid, amount)).thenReturn(paid(order, tid));
        paymentApprovalService.handleAuthResult(NicepayFixtures.authResult(order, tid));
        reset(nicepayClient);
        Payment paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        return paid;
    }

    // 승인을 요청했지만 결과를 받지 못해 APPROVING으로 남은 주문
    private Payment stuckOrder(int amount) {
        Payment order = order(amount);
        String tid = NicepayFixtures.newTid();
        when(nicepayClient.approve(tid, amount)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenThrow(new NicepayUnknownResultException("timeout", null));
        NicepayReturnRequestDto authResult = NicepayFixtures.authResult(order, tid);
        paymentApprovalService.handleAuthResult(authResult);
        reset(nicepayClient);
        Payment stuck = reload(order);
        assertThat(stuck.getStatus()).isEqualTo(PaymentStatus.APPROVING);
        return stuck;
    }

    // 승인 결과를 받지 못했고, 나중에 조회해 보니 그런 거래가 없어서 실패로 끝낸 주문
    private Payment failedOrder(int amount) {
        Payment stuck = stuckOrder(amount);
        when(nicepayClient.find(stuck.getTid())).thenReturn(NicepayFixtures.notFound());
        paymentApprovalService.settleStuckApproval(stuck);
        reset(nicepayClient);
        Payment failed = reload(stuck);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.FAILED);
        return failed;
    }

    // 환불을 요청했지만 취소 응답을 받지 못해 CANCELLING으로 남은 주문
    private Payment cancellingOrder(int amount) {
        Payment order = paidOrder(amount);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenThrow(new NicepayUnknownResultException("timeout", null));
        paymentCancelService.cancel(order.getOrderId(), "고객 요청", ADMIN_ID);
        reset(nicepayClient);
        Payment cancelling = reload(order);
        assertThat(cancelling.getStatus()).isEqualTo(PaymentStatus.CANCELLING);
        return cancelling;
    }

    private PaymentCancel onlyCancelOf(Payment order) {
        List<PaymentCancel> records = paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId());
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private int balance() {
        return creditQueryService.getSummary(userId).balance();
    }

    private void assertLedgerMatchesBalance() {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balance());
    }
}
