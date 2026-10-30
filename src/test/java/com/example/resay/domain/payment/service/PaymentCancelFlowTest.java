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
import com.example.resay.domain.user.entity.Role;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import com.example.resay.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static com.example.resay.domain.payment.service.NicepayFixtures.cancelled;
import static com.example.resay.domain.payment.service.NicepayFixtures.found;
import static com.example.resay.domain.payment.service.NicepayFixtures.notFound;
import static com.example.resay.domain.payment.service.NicepayFixtures.paid;
import static com.example.resay.domain.payment.service.NicepayFixtures.rejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 운영자 환불: 크레딧 회수 → 나이스페이 취소 → 확정(또는 되돌리기)까지의 흐름.
// 단계마다 따로 커밋되는 것을 확인해야 해서 테스트 트랜잭션으로 감싸지 않는다. 만든 데이터는 테스트마다 지운다.
// 나이스페이 API 호출만 목으로 바꾼다
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentCancelFlowTest {

    private static final String REASON = "고객 요청 (문의 #12)";
    // 크레딧을 회수한 뒤에 난 예외는 삼켜지고 "확인 중"으로 응답하므로 최종 상태만으로는 드러나지 않는다
    private static final String UNEXPECTED_ERROR_LOG = "충전 환불 처리 중 오류";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentApprovalService paymentApprovalService;

    @Autowired
    private PaymentReconciler paymentReconciler;

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

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private NicepayClient nicepayClient;

    private Long userId;
    private Long adminId;
    private String userToken;
    private String adminToken;
    private boolean unexpectedErrorIsPartOfTest;

    @BeforeEach
    void setUp() {
        long sequence = NicepayFixtures.nextSequence();
        userId = userRepository.saveAndFlush(User.createLocal(
                "cancel-flow-" + sequence + "@example.com", "encoded-password", "환불")).getId();
        adminId = userRepository.saveAndFlush(User.createLocal(
                "cancel-admin-" + sequence + "@example.com", "encoded-password", "운영자")).getId();
        userToken = jwtTokenProvider.issueAccessToken(userId, Role.USER.name()).value();
        adminToken = jwtTokenProvider.issueAccessToken(adminId, Role.ADMIN.name()).value();
    }

    @AfterEach
    void cleanUp(CapturedOutput output) {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentCancelRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        if (!unexpectedErrorIsPartOfTest) {
            assertThat(output.getOut()).doesNotContain(UNEXPECTED_ERROR_LOG).doesNotContain("Unhandled exception");
        }
    }

    @Test
    void cancelsPaymentAndCollectsCredits() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(cancelled(order));

        cancel(order)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT200_5"))
                .andExpect(jsonPath("$.result.orderId").value(order.getOrderId()))
                .andExpect(jsonPath("$.result.status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.cancelStatus").value("DONE"))
                .andExpect(jsonPath("$.result.amount").value(3000))
                .andExpect(jsonPath("$.result.credits").value(3000));

        Payment refunded = reload(order);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refunded.getCancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(balance()).isZero();

        List<CreditLedgerEntry> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger).extracting(CreditLedgerEntry::getType)
                .containsExactly(CreditLedgerType.CHARGE, CreditLedgerType.CHARGE_REFUND);
        assertThat(ledger.get(1).getAmount()).isEqualTo(-3000);
        assertThat(ledger.get(1).getBalanceAfter()).isZero();
        assertThat(ledger.get(1).getPaymentId()).isEqualTo(order.getId());

        PaymentCancel record = onlyCancelOf(order);
        assertThat(record.getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        assertThat(record.getUserId()).isEqualTo(userId);
        assertThat(record.getAmount()).isEqualTo(3000);
        assertThat(record.getCreditAmount()).isEqualTo(3000);
        assertThat(record.getReason()).isEqualTo(REASON);
        assertThat(record.getRequestedBy()).isEqualTo(adminId);
        assertThat(record.getCancelledAt()).isEqualTo(CANCELLED_AT);

        // 취소 요청에는 원래 주문번호가 아니라 새로 만든 번호를 넣고, 운영자가 적은 사유는 나이스페이로 보내지 않는다
        ArgumentCaptor<String> cancelOrderId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> sentReason = ArgumentCaptor.forClass(String.class);
        verify(nicepayClient, times(1)).cancel(eq(order.getTid()), cancelOrderId.capture(), sentReason.capture());
        assertThat(cancelOrderId.getValue()).isEqualTo(record.getCancelOrderId()).isNotEqualTo(order.getOrderId());
        assertThat(sentReason.getValue()).doesNotContain("문의");
    }

    // 취소를 요청하는 시점에는 크레딧이 이미 회수돼 있어야 한다.
    // 순서가 반대면 취소된 뒤 회수하기 전까지 "돈은 돌려받고 크레딧은 남은" 틈이 생긴다
    @Test
    void collectsCreditsBeforeRequestingCancel() throws Exception {
        Payment order = paidOrder(5000);
        AtomicReference<PaymentStatus> statusAtCancel = new AtomicReference<>();
        AtomicReference<Integer> balanceAtCancel = new AtomicReference<>();
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenAnswer(invocation -> {
            statusAtCancel.set(reload(order).getStatus());
            balanceAtCancel.set(balance());
            return cancelled(order);
        });

        cancel(order).andExpect(status().isOk());

        assertThat(statusAtCancel.get()).isEqualTo(PaymentStatus.CANCELLING);
        assertThat(balanceAtCancel.get()).isZero();
    }

    // 충전한 크레딧을 일부라도 썼으면 전액 취소하지 않는다. 나이스페이도 부르지 않는다
    @Test
    void rejectsWhenChargedCreditsWereUsed() throws Exception {
        Payment order = paidOrder(3000);
        creditService.useForAnalysis(userId, 77L, AnalysisCategory.DAILY);

        cancel(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("CREDIT409_2"))
                .andExpect(jsonPath("$.error.required").value(3000))
                .andExpect(jsonPath("$.error.balance").value(1500));

        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(1500);
        assertThat(paymentCancelRepository.findAll()).isEmpty();
        assertLedgerMatchesBalance();
    }

    // 크레딧은 어느 충전에서 온 것인지 구분하지 않는다. 남은 잔액이 그 충전 금액 이상이면 환불할 수 있다
    @Test
    void cancelsOneChargeWhenBalanceStillCoversIt() throws Exception {
        Payment first = paidOrder(3000);
        Payment second = paidOrder(3000);
        creditService.useForAnalysis(userId, 77L, AnalysisCategory.DAILY);
        when(nicepayClient.cancel(eq(first.getTid()), anyString(), anyString())).thenReturn(cancelled(first));

        cancel(first).andExpect(status().isOk());
        cancel(second).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CREDIT409_2"));

        assertThat(reload(first).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(reload(second).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(1500);
        verify(nicepayClient, never()).cancel(eq(second.getTid()), anyString(), anyString());
        assertLedgerMatchesBalance();
    }

    // 결제 완료 상태의 주문만 환불할 수 있다
    @Test
    void rejectsOrdersThatAreNotPaid() throws Exception {
        Payment approving = stuckOrder(3000);
        Payment failed = order(3000);
        paymentApprovalService.handleAuthResult(new NicepayReturnRequestDto(
                "I002", "사용자가 결제를 취소하였습니다.", null, null, failed.getOrderId(), "3000", null, null));
        Payment expired = order(3000);
        paymentReconciler.expireAbandonedOrders(LocalDateTime.now().plusHours(1));
        Payment ready = order(3000);
        assertThat(List.of(ready, approving, failed, expired)).extracting(order -> reload(order).getStatus())
                .containsExactly(
                        PaymentStatus.READY, PaymentStatus.APPROVING, PaymentStatus.FAILED, PaymentStatus.EXPIRED);

        for (Payment order : List.of(ready, approving, failed, expired)) {
            String currentStatus = reload(order).getStatus().name();
            cancel(order)
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("PAYMENT409_1"))
                    .andExpect(jsonPath("$.error.status").value(currentStatus));
            assertThat(reload(order).getStatus().name()).isEqualTo(currentStatus);
        }

        verifyNoInteractions(nicepayClient);
        assertThat(paymentCancelRepository.findAll()).isEmpty();
        assertThat(balance()).isZero();
    }

    // 이미 환불된 주문을 다시 환불하려 해도 크레딧을 또 회수하거나 취소를 또 요청하지 않는다
    @Test
    void doesNotCancelTwice() throws Exception {
        Payment order = paidOrder(3000);
        Payment other = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(cancelled(order));
        cancel(order).andExpect(status().isOk());

        cancel(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT409_1"))
                .andExpect(jsonPath("$.error.status").value("CANCELLED"));

        verify(nicepayClient, times(1)).cancel(anyString(), anyString(), anyString());
        // 다른 충전의 크레딧은 그대로다
        assertThat(balance()).isEqualTo(3000);
        assertThat(reload(other).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId())).hasSize(1);
        assertLedgerMatchesBalance();
    }

    // 나이스페이가 취소를 거절하면 회수한 크레딧을 되돌리고 주문은 결제 완료로 남는다
    @Test
    void restoresCreditsWhenNicepayRejectsCancel() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(rejected("2014", "취소 불가능 거래"));
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));

        cancel(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("PAYMENT409_2"))
                .andExpect(jsonPath("$.error.status").value("PAID"))
                .andExpect(jsonPath("$.error.cancelStatus").value("FAILED"))
                .andExpect(jsonPath("$.error.failCode").value("2014"))
                .andExpect(jsonPath("$.error.failMessage").value("취소 불가능 거래"));

        Payment kept = reload(order);
        assertThat(kept.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(kept.getCancelledAt()).isNull();
        assertThat(balance()).isEqualTo(3000);
        List<CreditLedgerEntry> ledger = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        assertThat(ledger).extracting(CreditLedgerEntry::getType).containsExactly(
                CreditLedgerType.CHARGE, CreditLedgerType.CHARGE_REFUND, CreditLedgerType.CHARGE_REFUND_UNDO);
        assertThat(ledger).extracting(CreditLedgerEntry::getAmount).containsExactly(3000, -3000, 3000);
        assertThat(ledger.get(2).getBalanceAfter()).isEqualTo(3000);

        PaymentCancel record = onlyCancelOf(order);
        assertThat(record.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
        assertThat(record.getFailCode()).isEqualTo("2014");
        assertThat(record.getCancelledAt()).isNull();
    }

    // 거절된 뒤 다시 요청할 수 있다. 시도마다 기록이 남고 취소 요청 번호도 새로 만든다
    @Test
    void canCancelAgainAfterRejection() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(rejected("2014", "취소 불가능 거래"))
                .thenReturn(cancelled(order));
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));
        cancel(order).andExpect(status().isConflict());

        cancel(order).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CANCELLED"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance()).isZero();
        List<PaymentCancel> records = paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId());
        assertThat(records).extracting(PaymentCancel::getStatus)
                .containsExactly(PaymentCancelStatus.FAILED, PaymentCancelStatus.DONE);
        assertThat(records.get(0).getCancelOrderId()).isNotEqualTo(records.get(1).getCancelOrderId());
        assertLedgerMatchesBalance();
    }

    // 거절 사유가 "이미 취소된 거래"인 경우 (나이스페이 관리자 화면에서 먼저 취소했다). 조회해 보고 환불로 끝낸다
    @Test
    void completesWhenRejectedBecauseAlreadyCancelled() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(rejected("2013", "취소 완료 거래"));
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        cancel(order).andExpect(status().isOk()).andExpect(jsonPath("$.result.status").value("CANCELLED"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance()).isZero();
        assertThat(onlyCancelOf(order).getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        assertLedgerMatchesBalance();
    }

    // 나이스페이에 그런 거래가 없다 (다른 상점 키로 결제된 주문 등). 환불되지 않았으므로 크레딧을 되돌린다
    @Test
    void restoresCreditsWhenTransactionDoesNotExistAtNicepay() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(notFound());
        when(nicepayClient.find(order.getTid())).thenReturn(notFound());

        cancel(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT409_2"))
                .andExpect(jsonPath("$.error.failCode").value("U107"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 취소 응답을 받지 못했다. 취소됐을 수도 있으므로 크레딧은 회수한 채로 두고 "확인 중"으로 답한다
    @Test
    void keepsCancellingWhenCancelResultIsUnknown() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenThrow(new NicepayUnknownResultException("timeout", null));

        cancel(order)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("PAYMENT202_1"))
                .andExpect(jsonPath("$.result.status").value("CANCELLING"))
                .andExpect(jsonPath("$.result.cancelStatus").value("REQUESTED"));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLING);
        assertThat(balance()).isZero();
        assertThat(onlyCancelOf(order).getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
        verify(nicepayClient, never()).find(anyString());

        // 확인 중인 주문에 환불을 다시 요청해도 취소를 또 보내지 않는다
        cancel(order)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PAYMENT409_1"))
                .andExpect(jsonPath("$.error.status").value("CANCELLING"));
        verify(nicepayClient, times(1)).cancel(anyString(), anyString(), anyString());
        assertThat(balance()).isZero();
    }

    // 정리 작업: 응답을 받지 못한 취소를 조회해 보니 취소돼 있다
    @Test
    void reconcilerCompletesCancelThatWentThrough() throws Exception {
        Payment order = cancellingOrder(3000);

        // 방금 요청한 취소는 아직 처리 중일 수 있어 건드리지 않는다
        paymentReconciler.settleStuckCancels(LocalDateTime.now());
        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLING);

        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));
        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(4));

        Payment refunded = reload(order);
        assertThat(refunded.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(refunded.getCancelledAt()).isEqualTo(CANCELLED_AT);
        assertThat(onlyCancelOf(order).getStatus()).isEqualTo(PaymentCancelStatus.DONE);
        assertThat(balance()).isZero();
        assertLedgerMatchesBalance();

        // 끝난 취소는 다시 조회하지 않는다
        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(8));
        verify(nicepayClient, times(1)).find(anyString());
    }

    // 정리 작업: 응답을 받지 못한 취소를 조회해 보니 결제가 그대로다 (요청이 나이스페이에 닿지 않았다). 크레딧을 되돌린다
    @Test
    void reconcilerRestoresCreditsWhenCancelDidNotHappen() throws Exception {
        Payment order = cancellingOrder(3000);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));

        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(4));
        // 정리 작업이 겹쳐 돌아도 한 번만 되돌린다
        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(5));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
        PaymentCancel record = onlyCancelOf(order);
        assertThat(record.getStatus()).isEqualTo(PaymentCancelStatus.FAILED);
        assertThat(record.getFailCode()).isEqualTo("NOT_CANCELLED");
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).extracting(CreditLedgerEntry::getType)
                .containsExactly(
                        CreditLedgerType.CHARGE, CreditLedgerType.CHARGE_REFUND, CreditLedgerType.CHARGE_REFUND_UNDO);
        assertLedgerMatchesBalance();
    }

    // 정리 작업: 취소 여부를 알 수 없으면 어느 쪽으로도 끝내지 않는다 (크레딧을 되돌렸는데 실제로는 취소됐다면 공짜 크레딧이 된다)
    @Test
    void reconcilerLeavesCancelWhenItCannotTell() throws Exception {
        Payment order = cancellingOrder(3000);
        LocalDateTime later = LocalDateTime.now().plusMinutes(4);

        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));
        paymentReconciler.settleStuckCancels(later);
        assertStillCancelling(order);

        // 조회가 거절됐다 (키 오류)
        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(new NicepayTransaction(
                401, "U116", "사용자 정보가 존재하지 않습니다.", null, null, null, 0, null, null, null, Signature.MISSING));
        paymentReconciler.settleStuckCancels(later);
        assertStillCancelling(order);

        // 전액 취소도 결제 완료도 아닌 상태 (나이스페이 관리자 화면에서 일부만 취소한 경우)
        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(found(order, "partialCancelled"));
        paymentReconciler.settleStuckCancels(later);
        assertStillCancelling(order);

        // 취소됐다는데 서명이 맞지 않는다
        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order, Signature.INVALID));
        paymentReconciler.settleStuckCancels(later);
        assertStillCancelling(order);

        // 결제 완료라는데 서명이 맞지 않는다
        reset(nicepayClient);
        when(nicepayClient.find(order.getTid())).thenReturn(new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", order.getTid(), order.getOrderId(), "paid", 3000,
                NicepayFixtures.PAID_AT, null, null, Signature.INVALID));
        paymentReconciler.settleStuckCancels(later);
        assertStillCancelling(order);
    }

    // 크레딧은 회수된 채로 환불이 멈춰 있는 상태가 오래가면 사람이 봐야 한다
    @Test
    void reconcilerReportsCancelThatStaysUnsettled(CapturedOutput output) throws Exception {
        Payment order = cancellingOrder(3000);
        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));

        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(30));
        assertThat(output.getOut()).doesNotContain("1시간 넘게 확인되지 않은");

        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusHours(2));
        assertThat(output.getOut()).contains("환불(결제 취소) 여부가 1시간 넘게 확인되지 않은 충전 주문이 1건");
        assertStillCancelling(order);
    }

    // 취소가 거절됐는데 확인하려던 조회도 답이 없다. 되돌리지 않고 정리 작업에 맡긴다
    @Test
    void keepsCancellingWhenRejectedButLookupIsUnanswered() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(rejected("2015", "기 취소 요청"));
        when(nicepayClient.find(order.getTid())).thenThrow(new NicepayUnknownResultException("timeout", null));

        cancel(order).andExpect(status().isAccepted()).andExpect(jsonPath("$.result.status").value("CANCELLING"));

        assertStillCancelling(order);
    }

    // 취소됐다는 응답의 서명이 맞지 않으면 확정하지 않는다. 되돌리지도 않는다
    @Test
    void doesNotSettleOnCancelResponseWithInvalidSignature() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(cancelled(order, Signature.INVALID));

        cancel(order).andExpect(status().isAccepted());

        assertStillCancelling(order);
        // 정리 작업이 조회로 확정한다
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));
        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance()).isZero();
    }

    // 서명이 없는 응답은 받아들인다 (필수 항목이 아니고, 우리가 직접 호출해 받은 응답이다)
    @Test
    void acceptsCancelResponseWithoutSignature() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenReturn(cancelled(order, Signature.MISSING));

        cancel(order).andExpect(status().isOk());

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
    }

    // 크레딧을 회수한 뒤 예상하지 못한 오류가 나도 운영자에게는 지금 상태를 알려 주고, 주문은 정리 작업이 이어받는다
    @Test
    void reportsCurrentStateWhenSomethingFailsAfterCollecting(CapturedOutput output) throws Exception {
        unexpectedErrorIsPartOfTest = true;
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenThrow(new IllegalStateException("예상하지 못한 오류"));

        cancel(order).andExpect(status().isAccepted()).andExpect(jsonPath("$.result.status").value("CANCELLING"));

        assertStillCancelling(order);
        assertThat(output.getOut()).contains(UNEXPECTED_ERROR_LOG);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));
        paymentReconciler.settleStuckCancels(LocalDateTime.now().plusMinutes(4));
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    // 승인 응답이 아주 늦게 도착했는데, 그사이 정리 작업이 결제를 확정했고 운영자가 환불까지 마친 경우.
    // 늦게 온 "결제 완료" 응답을 "결제되지 않고 끝난 주문에 잡힌 결제"로 보고 취소를 또 요청하면 안 된다
    @Test
    void lateApprovalResponseDoesNotTouchRefundedOrder(CapturedOutput output) throws Exception {
        Payment order = order(3000);
        String tid = NicepayFixtures.newTid();
        when(nicepayClient.find(tid)).thenAnswer(invocation -> paid(reload(order), tid));
        when(nicepayClient.cancel(eq(tid), anyString(), anyString()))
                .thenAnswer(invocation -> cancelled(reload(order)));
        when(nicepayClient.approve(tid, 3000)).thenAnswer(invocation -> {
            paymentApprovalService.settleStuckApproval(reload(order));
            cancel(reload(order)).andExpect(status().isOk());
            return paid(order, tid);
        });

        paymentApprovalService.handleAuthResult(NicepayFixtures.authResult(order, tid));

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(balance()).isZero();
        // 운영자의 환불 한 번뿐이다
        verify(nicepayClient, times(1)).cancel(anyString(), anyString(), anyString());
        assertThat(output.getOut()).doesNotContain("이미 끝난 주문에 결제 완료 응답이 왔습니다");
        assertLedgerMatchesBalance();
    }

    @Test
    void returnsNotFoundForUnknownOrder() throws Exception {
        mockMvc.perform(post("/api/v1/admin/payments/{orderId}/cancel", "RS000000unknown")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"테스트\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT404_1"));
    }

    // 사용자가 자기 결제를 직접 취소할 수 없어야 한다 (환불은 운영자가 처리한다)
    @Test
    void onlyAdminCanCancel() throws Exception {
        Payment order = paidOrder(3000);

        cancel(order, userToken, REASON)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403_1"));
        mockMvc.perform(post("/api/v1/admin/payments/{orderId}/cancel", order.getOrderId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"테스트\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balance()).isEqualTo(3000);
    }

    @Test
    void requiresReason() throws Exception {
        Payment order = paidOrder(3000);

        cancel(order, adminToken, "  ").andExpect(status().isBadRequest());
        cancel(order, adminToken, "가".repeat(201)).andExpect(status().isBadRequest());

        verifyNoInteractions(nicepayClient);
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
    }

    // 환불된 충전은 사용자의 충전 내역에 환불 상태로 남고, 크레딧 내역에는 회수한 줄이 보인다
    @Test
    void refundedChargeStaysInUserHistory() throws Exception {
        Payment order = paidOrder(3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(cancelled(order));
        cancel(order).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/payments").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items.length()").value(1))
                .andExpect(jsonPath("$.result.items[0].orderId").value(order.getOrderId()))
                .andExpect(jsonPath("$.result.items[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.result.items[0].cancelledAt").isNotEmpty());
        mockMvc.perform(get("/api/v1/payments/{orderId}", order.getOrderId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("CANCELLED"));
        mockMvc.perform(get("/api/v1/credits/history").header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.items[0].type").value("CHARGE_REFUND"))
                .andExpect(jsonPath("$.result.items[0].amount").value(-3000))
                .andExpect(jsonPath("$.result.items[0].balanceAfter").value(0));
    }

    private void assertStillCancelling(Payment order) {
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLING);
        assertThat(onlyCancelOf(order).getStatus()).isEqualTo(PaymentCancelStatus.REQUESTED);
        // 크레딧은 회수된 채다
        assertThat(balance()).isZero();
        assertLedgerMatchesBalance();
    }

    private ResultActions cancel(Payment order) throws Exception {
        return cancel(order, adminToken, REASON);
    }

    private ResultActions cancel(Payment order, String token, String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/payments/{orderId}/cancel", order.getOrderId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + reason + "\"}"));
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
        paymentApprovalService.handleAuthResult(NicepayFixtures.authResult(order, tid));
        reset(nicepayClient);
        return reload(order);
    }

    // 환불을 요청했지만 취소 응답을 받지 못해 CANCELLING으로 남은 주문
    private Payment cancellingOrder(int amount) throws Exception {
        Payment order = paidOrder(amount);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenThrow(new NicepayUnknownResultException("timeout", null));
        cancel(order).andExpect(status().isAccepted());
        reset(nicepayClient);
        return reload(order);
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
