package com.example.resay.domain.payment.service;

import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.domain.payment.dto.NicepayReturnRequestDto;
import com.example.resay.domain.payment.entity.Payment;
import com.example.resay.domain.payment.entity.PaymentStatus;
import com.example.resay.domain.payment.repository.PaymentRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.nicepay.NicepayClient;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction;
import com.example.resay.global.infrastructure.nicepay.NicepayTransaction.Signature;
import com.example.resay.global.infrastructure.nicepay.NicepayUnknownResultException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 같은 결제를 여러 요청이 동시에 처리하려 해도 승인과 크레딧 지급이 한 번만 일어나는지 확인한다.
// 잠금 동작은 DB마다 다르므로 실제 MySQL로도 같은 테스트를 돌려 확인한다 (PR 본문 참고)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class PaymentConcurrencyTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final String CLIENT_KEY = "S2_test-client-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final Long ADMIN_ID = 999_999L;
    private static final int THREADS = 12;

    @Autowired
    private PaymentApprovalService paymentApprovalService;

    @Autowired
    private PaymentReconciler paymentReconciler;

    @Autowired
    private PaymentRepository paymentRepository;

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
        paymentRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        // 인증 결과 처리 중에 난 예외(교착, 잠금 대기 초과 등)는 삼켜지고 로그만 남는다.
        // 다른 스레드가 주문을 마저 끝내면 최종 상태만으로는 드러나지 않으므로 로그로 확인한다
        assertThat(output.getOut()).doesNotContain("결제 인증 결과 처리 중 오류");
    }

    // 같은 인증 결과가 동시에 여러 번 도착해도(연속 새로고침 등) 승인 요청과 지급은 한 번이다
    @Test
    void approvesAndGrantsOnceWhenSameAuthResultArrivesConcurrently() throws Exception {
        Long userId = newUser();
        Payment order = order(userId, 5000);
        String tid = newTid();
        when(nicepayClient.approve(tid, 5000)).thenAnswer(invocation -> {
            // 승인 요청이 진행되는 동안 나머지 요청이 도착하게 한다
            Thread.sleep(200);
            return paid(order, tid);
        });

        assertThat(runConcurrently(index ->
                () -> paymentApprovalService.handleAuthResult(authResult(order, tid)))).isEmpty();

        verify(nicepayClient, times(1)).approve(anyString(), anyInt());
        assertThat(reload(order).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balanceOf(userId)).isEqualTo(5000);
        assertThat(chargeCount(userId)).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    // 정리 작업이 겹쳐 돌거나 늦게 도착한 요청과 겹쳐도 결제 확정과 지급은 한 번이다
    @Test
    void grantsOnceWhenStuckApprovalIsSettledConcurrently() throws Exception {
        Long userId = newUser();
        Payment stuck = stuckOrder(userId, 3000);
        when(nicepayClient.find(stuck.getTid())).thenReturn(paid(stuck, stuck.getTid()));

        assertThat(runConcurrently(index -> () -> {
            paymentApprovalService.settleStuckApproval(stuck);
            return null;
        })).isEmpty();

        assertThat(reload(stuck).getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(balanceOf(userId)).isEqualTo(3000);
        assertThat(chargeCount(userId)).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    // 한쪽은 결제됐다고, 다른 쪽은 결제되지 않았다고 판단해도 주문은 한 가지 결과로만 끝나고 크레딧과 어긋나지 않는다
    @Test
    void endsInExactlyOneOutcomeWhenPaidAndFailedRace() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long userId = newUser();
            Payment stuck = stuckOrder(userId, 3000);
            NicepayTransaction paid = paid(stuck, stuck.getTid());
            NicepayTransaction cancelled = new NicepayTransaction(
                    200, "0000", "정상 처리되었습니다.", stuck.getTid(), stuck.getOrderId(), "cancelled", 3000,
                    null, null, Signature.VALID);
            AtomicLong calls = new AtomicLong();
            when(nicepayClient.find(stuck.getTid()))
                    .thenAnswer(invocation -> calls.incrementAndGet() % 2 == 0 ? paid : cancelled);

            assertThat(runConcurrently(index -> () -> {
                paymentApprovalService.settleStuckApproval(stuck);
                return null;
            })).isEmpty();

            PaymentStatus status = reload(stuck).getStatus();
            assertThat(status).isIn(PaymentStatus.PAID, PaymentStatus.FAILED);
            assertThat(balanceOf(userId)).isEqualTo(status == PaymentStatus.PAID ? 3000 : 0);
            assertThat(chargeCount(userId)).isEqualTo(status == PaymentStatus.PAID ? 1 : 0);
            assertLedgerMatchesBalance(userId);
        }
    }

    // 인증 결과가 도착하는 순간에 주문이 만료 처리돼도 "만료됐는데 결제됨"이나 "결제됐는데 만료됨"은 없다
    @Test
    void expirationAndApprovalNeverBothWin() throws Exception {
        for (int round = 0; round < 5; round++) {
            Long userId = newUser();
            Payment order = order(userId, 1000);
            String tid = newTid();
            when(nicepayClient.approve(tid, 1000)).thenReturn(paid(order, tid));

            assertThat(runConcurrently(index -> () -> {
                if (index % 2 == 0) {
                    paymentReconciler.expireAbandonedOrders(LocalDateTime.now().plusHours(1));
                } else {
                    paymentApprovalService.handleAuthResult(authResult(order, tid));
                }
                return null;
            })).isEmpty();

            PaymentStatus status = reload(order).getStatus();
            assertThat(status).isIn(PaymentStatus.PAID, PaymentStatus.EXPIRED);
            assertThat(balanceOf(userId)).isEqualTo(status == PaymentStatus.PAID ? 1000 : 0);
            verify(nicepayClient, times(status == PaymentStatus.PAID ? 1 : 0)).approve(anyString(), anyInt());
            assertLedgerMatchesBalance(userId);
            reset(nicepayClient);
        }
    }

    // 한 사용자의 충전 여러 건과 크레딧 사용 · 환급 · 조정이 동시에 몰려도 잔액과 장부가 어긋나지 않는다
    @Test
    void keepsLedgerConsistentWhenChargesAndUsagesOverlap() throws Exception {
        Long userId = newUser();
        creditService.adjust(userId, 20_000, "테스트 지급", ADMIN_ID);
        List<Payment> orders = new ArrayList<>();
        List<String> tids = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            Payment order = order(userId, 1000);
            String tid = newTid();
            when(nicepayClient.approve(tid, 1000)).thenReturn(paid(order, tid));
            orders.add(order);
            tids.add(tid);
            creditService.useForAnalysis(userId, 100L + index, AnalysisCategory.DAILY);
        }
        int balanceBefore = balanceOf(userId);

        List<Throwable> failures = runConcurrently(index -> () -> {
            switch (index % 3) {
                case 0 -> creditService.refund(UsagePurpose.ANALYSIS, 100L + index, "실패");
                case 1 -> creditService.adjust(userId, -100, "회수 " + index, ADMIN_ID);
                default -> creditService.useForCharacter(userId, 500L + index);
            }
            paymentApprovalService.handleAuthResult(authResult(orders.get(index), tids.get(index)));
            return null;
        });

        assertThat(failures).isEmpty();
        assertThat(paymentRepository.findAll()).allMatch(payment -> payment.getStatus() == PaymentStatus.PAID);
        assertThat(chargeCount(userId)).isEqualTo(THREADS);
        // 환급 4건(+1,500), 회수 4건(−100), 캐릭터 4건(첫 1건 무료, 나머지 −500), 충전 12건(+1,000)
        assertThat(balanceOf(userId)).isEqualTo(balanceBefore + 4 * 1500 - 4 * 100 - 3 * 500 + THREADS * 1000);
        assertLedgerMatchesBalance(userId);
    }

    // 서로 다른 사용자의 결제는 서로를 막거나 교착시키지 않아야 한다 (런칭 직후 여러 사람이 동시에 충전하는 상황)
    @Test
    void differentUsersDoNotBlockOrDeadlockEachOther() throws Exception {
        List<Long> userIds = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            userIds.add(newUser());
        }

        for (int round = 0; round < 5; round++) {
            List<Payment> orders = new ArrayList<>();
            List<String> tids = new ArrayList<>();
            for (Long userId : userIds) {
                Payment order = order(userId, 3000);
                String tid = newTid();
                when(nicepayClient.approve(tid, 3000)).thenReturn(paid(order, tid));
                orders.add(order);
                tids.add(tid);
            }
            long base = 10_000L * (round + 1);

            List<Throwable> failures = runConcurrently(index -> () -> {
                Long userId = userIds.get(index);
                paymentApprovalService.handleAuthResult(authResult(orders.get(index), tids.get(index)));
                // 충전하자마자 분석을 결제하고, 같은 인증 결과가 한 번 더 도착한다
                creditService.useForAnalysis(userId, base + index, AnalysisCategory.CONFLICT);
                paymentApprovalService.handleAuthResult(authResult(orders.get(index), tids.get(index)));
                return null;
            });

            assertThat(failures).isEmpty();
        }

        for (Long userId : userIds) {
            assertThat(balanceOf(userId)).isEqualTo(5 * (3000 - 2000));
            assertThat(chargeCount(userId)).isEqualTo(5);
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
                        // 순서에 따라 생길 수 있는 업무 오류(잔액 부족 등)는 호출한 테스트가 결과로 확인한다
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
        long sequence = SEQUENCE.incrementAndGet();
        return userRepository.saveAndFlush(
                User.createLocal("charge-race-" + sequence + "@example.com", "encoded-password", "동시" + sequence)
        ).getId();
    }

    private Payment order(Long userId, int amount) {
        return paymentRepository.saveAndFlush(Payment.create(
                userId,
                "RS261008" + String.format("%032x", SEQUENCE.incrementAndGet()),
                "CREDIT_" + amount, amount, amount, "http://localhost:3000"));
    }

    // 승인을 요청했지만 결과를 받지 못해 APPROVING으로 남은 주문
    private Payment stuckOrder(Long userId, int amount) {
        Payment order = order(userId, amount);
        String tid = newTid();
        when(nicepayClient.approve(tid, amount)).thenThrow(new NicepayUnknownResultException("timeout", null));
        when(nicepayClient.netCancel(order.getOrderId())).thenThrow(new NicepayUnknownResultException("timeout", null));
        paymentApprovalService.handleAuthResult(authResult(order, tid));
        reset(nicepayClient);
        Payment stuck = reload(order);
        assertThat(stuck.getStatus()).isEqualTo(PaymentStatus.APPROVING);
        return stuck;
    }

    private NicepayReturnRequestDto authResult(Payment order, String tid) {
        // 인증 결과마다 토큰이 다르다 (같은 토큰은 한 주문에만 쓸 수 있다)
        String authToken = "NICETOKEN" + tid;
        return new NicepayReturnRequestDto(
                "0000", "인증 성공", tid, CLIENT_KEY, order.getOrderId(), String.valueOf(order.getAmount()),
                authToken, sign(authToken + CLIENT_KEY + order.getAmount() + SECRET_KEY));
    }

    private NicepayTransaction paid(Payment order, String tid) {
        return new NicepayTransaction(
                200, "0000", "정상 처리되었습니다.", tid, order.getOrderId(), "paid", order.getAmount(),
                LocalDateTime.now(), null, Signature.VALID);
    }

    private Payment reload(Payment payment) {
        return paymentRepository.findById(payment.getId()).orElseThrow();
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private long chargeCount(Long userId) {
        return ledgerRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(entry -> entry.getType() == CreditLedgerType.CHARGE)
                .count();
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }

    private static String newTid() {
        return "UT0000113m0101261008" + String.format("%010d", SEQUENCE.incrementAndGet());
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
