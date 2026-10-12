package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentCancel;
import com.example.resay.domain.payment.entity.PaymentCancelStatus;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentCancelRepository;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static com.example.resay.domain.payment.service.NicepayFixtures.cancelled;
import static com.example.resay.domain.payment.service.NicepayFixtures.paid;
import static com.example.resay.domain.payment.service.NicepayFixtures.rejected;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 같은 환불을 여러 곳(운영자 요청, 정리 작업, 웹훅)이 동시에 처리하려 해도 크레딧 회수 · 되돌리기 · 취소 요청이 한 번만 일어나는지,
// 환불과 크레딧 사용 · 충전이 겹쳐도 잔액과 장부가 어긋나지 않는지 확인한다.
// 잠금 동작은 DB마다 다르므로 실제 MySQL로도 같은 테스트를 돌려 확인한다 (PR 본문 참고)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentCancelConcurrencyTest {

    private static final Long ADMIN_ID = 999_999L;
    private static final int THREADS = 12;
    private static final int ROUNDS = 5;

    @Autowired
    private PaymentApprovalService paymentApprovalService;

    @Autowired
    private PaymentCancelService paymentCancelService;

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

    @MockitoBean
    private NicepayClient nicepayClient;

    @AfterEach
    void cleanUp(CapturedOutput output) {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        paymentCancelRepository.deleteAllInBatch();
        paymentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        // 크레딧을 회수한 뒤에 난 예외(교착, 잠금 대기 초과 등)는 삼켜지고 로그만 남는다.
        // 다른 스레드가 환불을 마저 끝내면 최종 상태만으로는 드러나지 않으므로 로그로 확인한다
        assertThat(output.getOut())
                .doesNotContain("충전 환불 처리 중 오류")
                .doesNotContain("결제 인증 결과 처리 중 오류")
                .doesNotContain("취소 중인 주문을 정리하지 못했습니다");
    }

    // 같은 주문의 환불 요청이 동시에 여러 번 와도(버튼 연타 등) 크레딧 회수와 취소 요청은 한 번이다
    @Test
    void refundsOnceWhenSameRefundIsRequestedConcurrently() throws Exception {
        Long userId = newUser();
        Payment order = paidOrder(userId, 5000);
        Payment other = paidOrder(userId, 3000);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenAnswer(invocation -> {
            // 취소 요청이 진행되는 동안 나머지 요청이 도착하게 한다
            Thread.sleep(200);
            return cancelled(order);
        });

        assertThat(runConcurrently(index ->
                () -> paymentCancelService.cancel(order.getOrderId(), "동시 요청 " + index, ADMIN_ID))).isEmpty();

        verify(nicepayClient, times(1)).cancel(anyString(), anyString(), anyString());
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(reload(other).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId())).hasSize(1);
        // 다른 충전의 3,000만 남는다
        assertThat(balanceOf(userId)).isEqualTo(3000);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(1);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND_UNDO)).isZero();
        assertLedgerMatchesBalance(userId);
    }

    // 환불과 크레딧 사용이 같은 순간에 일어나도 "환불도 받고 그 크레딧으로 분석도 한" 경우는 없다
    @Test
    void refundAndUsageNeverBothSpendTheSameCredits() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long userId = newUser();
            Payment order = paidOrder(userId, 3000);
            when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString())).thenReturn(cancelled(order));
            long recordingBase = 1_000L * (round + 1);

            assertThat(runConcurrently(index -> () -> {
                if (index % 2 == 0) {
                    paymentCancelService.cancel(order.getOrderId(), "환불", ADMIN_ID);
                } else {
                    // 2,000 크레딧. 3,000으로는 한 건만 결제할 수 있다
                    creditService.useForAnalysis(userId, recordingBase + index, AnalysisCategory.CONFLICT);
                }
                return null;
            })).isEmpty();

            PaymentStatus status = reload(order).getStatus();
            assertThat(status).isIn(PaymentStatus.CANCELLED, PaymentStatus.PAID);
            boolean refunded = status == PaymentStatus.CANCELLED;
            assertThat(balanceOf(userId)).isEqualTo(refunded ? 0 : 1000);
            assertThat(count(userId, CreditLedgerType.USE)).isEqualTo(refunded ? 0 : 1);
            assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(refunded ? 1 : 0);
            verify(nicepayClient, times(refunded ? 1 : 0)).cancel(anyString(), anyString(), anyString());
            assertLedgerMatchesBalance(userId);
            reset(nicepayClient);
        }
    }

    // 응답을 받지 못한 취소를 정리 작업 · 웹훅이 동시에 확정해도 환불 완료는 한 번이고 크레딧을 또 회수하지 않는다
    @Test
    void completesOnceWhenPendingCancelIsSettledConcurrently() throws Exception {
        Long userId = newUser();
        Payment order = cancellingOrder(userId, 3000);
        PaymentCancel pending = pendingCancelOf(order);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));
        LocalDateTime later = LocalDateTime.now().plusMinutes(4);

        assertThat(runConcurrently(index -> () -> {
            switch (index % 3) {
                case 0 -> paymentReconciler.settleStuckCancels(later);
                case 1 -> paymentCancelService.syncCancelledAtNicepay(order);
                default -> paymentCancelService.settleStuckCancel(pending);
            }
            return null;
        })).isEmpty();

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId()))
                .extracting(PaymentCancel::getStatus).containsExactly(PaymentCancelStatus.DONE);
        assertThat(balanceOf(userId)).isZero();
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(1);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND_UNDO)).isZero();
        assertLedgerMatchesBalance(userId);
    }

    // 취소되지 않은 것으로 확인된 건을 여러 곳이 동시에 끝내도 크레딧은 한 번만 되돌린다
    @Test
    void restoresCreditsOnceWhenPendingCancelIsRejectedConcurrently() throws Exception {
        Long userId = newUser();
        Payment order = cancellingOrder(userId, 3000);
        PaymentCancel pending = pendingCancelOf(order);
        when(nicepayClient.find(order.getTid())).thenReturn(paid(order, order.getTid()));
        LocalDateTime later = LocalDateTime.now().plusMinutes(4);

        assertThat(runConcurrently(index -> () -> {
            if (index % 2 == 0) {
                paymentReconciler.settleStuckCancels(later);
            } else {
                paymentCancelService.settleStuckCancel(pending);
            }
            return null;
        })).isEmpty();

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balanceOf(userId)).isEqualTo(3000);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(1);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND_UNDO)).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    // 한쪽은 취소됐다고, 다른 쪽은 취소되지 않았다고 판단해도 주문은 한 가지 결과로만 끝나고 크레딧과 어긋나지 않는다
    @Test
    void endsInExactlyOneOutcomeWhenCancelledAndNotCancelledRace() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long userId = newUser();
            Payment order = cancellingOrder(userId, 3000);
            PaymentCancel pending = pendingCancelOf(order);
            NicepayTransaction stillPaid = paid(order, order.getTid());
            NicepayTransaction nowCancelled = cancelled(order);
            AtomicLong calls = new AtomicLong();
            when(nicepayClient.find(order.getTid()))
                    .thenAnswer(invocation -> calls.incrementAndGet() % 2 == 0 ? stillPaid : nowCancelled);

            assertThat(runConcurrently(index -> () -> {
                paymentCancelService.settleStuckCancel(pending);
                return null;
            })).isEmpty();

            PaymentStatus status = reload(order).getStatus();
            assertThat(status).isIn(PaymentStatus.CANCELLED, PaymentStatus.PAID);
            boolean refunded = status == PaymentStatus.CANCELLED;
            assertThat(pendingOrSettled(order).getStatus())
                    .isEqualTo(refunded ? PaymentCancelStatus.DONE : PaymentCancelStatus.FAILED);
            assertThat(balanceOf(userId)).isEqualTo(refunded ? 0 : 3000);
            assertThat(count(userId, CreditLedgerType.CHARGE_REFUND_UNDO)).isEqualTo(refunded ? 0 : 1);
            assertLedgerMatchesBalance(userId);
            reset(nicepayClient);
        }
    }

    // 나이스페이 쪽에서 취소된 결제의 통보가 겹쳐 와도 크레딧은 한 번만 회수한다
    @Test
    void collectsOnceWhenOutsideCancelIsReflectedConcurrently() throws Exception {
        Long userId = newUser();
        Payment order = paidOrder(userId, 3000);
        paidOrder(userId, 5000);
        when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

        assertThat(runConcurrently(index -> () -> {
            paymentCancelService.syncCancelledAtNicepay(order);
            return null;
        })).isEmpty();

        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId())).hasSize(1);
        assertThat(balanceOf(userId)).isEqualTo(5000);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    // 운영자가 환불을 누르는 순간 나이스페이 쪽 취소 통보가 도착해도 크레딧을 두 번 회수하지 않는다
    @Test
    void refundRequestAndOutsideCancelNeverCollectTwice() throws Exception {
        for (int round = 0; round < ROUNDS; round++) {
            Long userId = newUser();
            Payment order = paidOrder(userId, 3000);
            paidOrder(userId, 5000);
            // 이미 취소된 거래라 우리 쪽 취소 요청은 거절되고, 조회하면 취소돼 있다
            when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                    .thenReturn(rejected("2013", "취소 완료 거래"));
            when(nicepayClient.find(order.getTid())).thenReturn(cancelled(order));

            assertThat(runConcurrently(index -> () -> {
                if (index % 2 == 0) {
                    paymentCancelService.cancel(order.getOrderId(), "환불", ADMIN_ID);
                } else {
                    paymentCancelService.syncCancelledAtNicepay(order);
                }
                return null;
            })).isEmpty();

            assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId()))
                    .extracting(PaymentCancel::getStatus).containsExactly(PaymentCancelStatus.DONE);
            assertThat(balanceOf(userId)).isEqualTo(5000);
            assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(1);
            assertLedgerMatchesBalance(userId);
            reset(nicepayClient);
        }
    }

    // 한 사용자의 환불 여러 건과 충전 · 크레딧 사용이 동시에 몰려도 서로를 막거나 교착시키지 않고 잔액과 장부가 맞는다
    @Test
    void keepsLedgerConsistentWhenRefundsChargesAndUsagesOverlap() throws Exception {
        Long userId = newUser();
        creditService.adjust(userId, 20_000, "테스트 지급", ADMIN_ID);
        List<Payment> refunds = new ArrayList<>();
        List<Payment> charges = new ArrayList<>();
        List<String> chargeTids = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            Payment toRefund = paidOrder(userId, 1000);
            when(nicepayClient.cancel(eq(toRefund.getTid()), anyString(), anyString())).thenReturn(cancelled(toRefund));
            refunds.add(toRefund);
            Payment toCharge = order(userId, 3000);
            String tid = NicepayFixtures.newTid();
            when(nicepayClient.approve(tid, 3000)).thenReturn(paid(toCharge, tid));
            charges.add(toCharge);
            chargeTids.add(tid);
        }
        int balanceBefore = balanceOf(userId);

        assertThat(runConcurrently(index -> () -> {
            paymentCancelService.cancel(refunds.get(index).getOrderId(), "환불 " + index, ADMIN_ID);
            creditService.useForAnalysis(userId, 500L + index, AnalysisCategory.DAILY);
            paymentApprovalService.handleAuthResult(
                    NicepayFixtures.authResult(charges.get(index), chargeTids.get(index)));
            return null;
        })).isEmpty();

        assertThat(refunds).allMatch(order -> reload(order).getStatus() == PaymentStatus.CANCELLED);
        assertThat(charges).allMatch(order -> reload(order).getStatus() == PaymentStatus.PAID);
        assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(THREADS);
        // 환불 12건(−1,000), 분석 12건(−1,500), 충전 12건(+3,000)
        assertThat(balanceOf(userId)).isEqualTo(balanceBefore + THREADS * (-1000 - 1500 + 3000));
        assertLedgerMatchesBalance(userId);
    }

    // 서로 다른 사용자의 환불은 서로를 막거나 교착시키지 않아야 한다
    @Test
    void differentUsersDoNotBlockOrDeadlockEachOther() throws Exception {
        List<Long> userIds = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            userIds.add(newUser());
        }

        for (int round = 0; round < ROUNDS; round++) {
            List<Payment> refundable = new ArrayList<>();
            List<Payment> unrefundable = new ArrayList<>();
            for (Long userId : userIds) {
                Payment toRefund = paidOrder(userId, 3000);
                when(nicepayClient.cancel(eq(toRefund.getTid()), anyString(), anyString()))
                        .thenReturn(cancelled(toRefund));
                when(nicepayClient.find(toRefund.getTid())).thenReturn(cancelled(toRefund));
                refundable.add(toRefund);
                // 거절되는 환불: 회수 → 거절 → 되돌리기까지 간다
                Payment toReject = paidOrder(userId, 1000);
                when(nicepayClient.cancel(eq(toReject.getTid()), anyString(), anyString()))
                        .thenReturn(rejected("2014", "취소 불가능 거래"));
                when(nicepayClient.find(toReject.getTid())).thenReturn(paid(toReject, toReject.getTid()));
                unrefundable.add(toReject);
            }

            assertThat(runConcurrently(index -> () -> {
                paymentCancelService.cancel(refundable.get(index).getOrderId(), "환불", ADMIN_ID);
                try {
                    paymentCancelService.cancel(unrefundable.get(index).getOrderId(), "환불", ADMIN_ID);
                } catch (GeneralException expected) {
                    // 취소 거절
                }
                // 환불 직후 취소 통보가 도착한다
                paymentCancelService.syncCancelledAtNicepay(refundable.get(index));
                return null;
            })).isEmpty();
            reset(nicepayClient);
        }

        for (Long userId : userIds) {
            // 라운드마다 3,000은 환불되고 1,000은 환불이 거절돼 남는다
            assertThat(balanceOf(userId)).isEqualTo(ROUNDS * 1000);
            assertThat(count(userId, CreditLedgerType.CHARGE_REFUND)).isEqualTo(ROUNDS * 2L);
            assertThat(count(userId, CreditLedgerType.CHARGE_REFUND_UNDO)).isEqualTo(ROUNDS);
            assertLedgerMatchesBalance(userId);
        }
    }

    // 동시에 실행하고, 작업이 던진 예외를 모아 돌려준다
    private List<Throwable> runConcurrently(IntFunction<Callable<Object>> taskFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                Callable<Object> task = taskFactory.apply(index);
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        task.call();
                        return null;
                    } catch (GeneralException exception) {
                        // 순서에 따라 생길 수 있는 업무 오류(이미 환불 중, 잔액 부족 등)는 호출한 테스트가 결과로 확인한다
                        return null;
                    } catch (Throwable throwable) {
                        return throwable;
                    }
                }));
            }
            start.countDown();

            List<Throwable> failures = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                Throwable failure = future.get(60, TimeUnit.SECONDS);
                if (failure != null) {
                    failures.add(failure);
                }
            }
            return failures;
        } finally {
            // 아직 도는 작업이 남은 채로 다음 테스트의 정리가 실행되지 않게 끝날 때까지 기다린다
            executor.shutdownNow();
            executor.awaitTermination(60, TimeUnit.SECONDS);
        }
    }

    private Long newUser() {
        long sequence = NicepayFixtures.nextSequence();
        return userRepository.saveAndFlush(
                User.createLocal("refund-race-" + sequence + "@example.com", "encoded-password", "동시" + sequence)
        ).getId();
    }

    private Payment order(Long userId, int amount) {
        return paymentRepository.saveAndFlush(NicepayFixtures.newOrder(userId, amount));
    }

    // 결제까지 끝나 크레딧이 지급된 주문
    private Payment paidOrder(Long userId, int amount) {
        Payment order = order(userId, amount);
        String tid = NicepayFixtures.newTid();
        when(nicepayClient.approve(tid, amount)).thenReturn(paid(order, tid));
        paymentApprovalService.handleAuthResult(NicepayFixtures.authResult(order, tid));
        Payment paid = reload(order);
        assertThat(paid.getStatus()).isEqualTo(PaymentStatus.PAID);
        return paid;
    }

    // 환불을 요청했지만 취소 응답을 받지 못해 CANCELLING으로 남은 주문
    private Payment cancellingOrder(Long userId, int amount) {
        Payment order = paidOrder(userId, amount);
        when(nicepayClient.cancel(eq(order.getTid()), anyString(), anyString()))
                .thenThrow(new NicepayUnknownResultException("timeout", null));
        paymentCancelService.cancel(order.getOrderId(), "환불", ADMIN_ID);
        reset(nicepayClient);
        Payment cancelling = reload(order);
        assertThat(cancelling.getStatus()).isEqualTo(PaymentStatus.CANCELLING);
        return cancelling;
    }

    private PaymentCancel pendingCancelOf(Payment order) {
        return paymentCancelRepository
                .findFirstByPaymentIdAndStatusOrderByIdDesc(order.getId(), PaymentCancelStatus.REQUESTED)
                .orElseThrow();
    }

    private PaymentCancel pendingOrSettled(Payment order) {
        List<PaymentCancel> records = paymentCancelRepository.findByPaymentIdOrderByIdAsc(order.getId());
        assertThat(records).hasSize(1);
        return records.get(0);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private long count(Long userId, CreditLedgerType type) {
        return ledgerRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(entry -> entry.getType() == type)
                .count();
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }
}
