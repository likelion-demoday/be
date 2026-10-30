package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.model.UsageResult;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

// 같은 사용자의 요청이 동시에 몰려도 잔액과 장부가 어긋나지 않는지 확인한다.
// 잠금 동작은 DB마다 다르므로 실제 MySQL로도 같은 테스트를 돌려 확인한다 (PR 본문 참고)
@SpringBootTest
class CreditConcurrencyTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Long ADMIN_ID = 999_999L;
    private static final int THREADS = 12;

    @Autowired
    private CreditService creditService;

    @Autowired
    private CreditQueryService creditQueryService;

    @Autowired
    private CreditWalletRepository walletRepository;

    @Autowired
    private CreditUsageRepository usageRepository;

    @Autowired
    private CreditLedgerRepository ledgerRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void neverSpendsMoreThanBalance() throws Exception {
        Long userId = userWithCredits(5000);

        // 서로 다른 녹음 12건을 동시에 결제한다. 5,000으로는 1,500짜리 3건만 가능하다
        List<Outcome> outcomes = runConcurrently(index ->
                () -> creditService.useForAnalysis(userId, 100L + index, AnalysisCategory.DAILY));

        assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(3);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode)
                .containsOnly("CREDIT402_1");
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(usageRepository.count()).isEqualTo(3);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void deductsOnceWhenSameRecordingIsRequestedConcurrently() throws Exception {
        Long userId = userWithCredits(5000);

        List<Outcome> outcomes = runConcurrently(index ->
                () -> creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY));

        // 한 건만 실제로 차감하고, 나머지는 "이미 차감됨"(성공) 또는 "처리 중"(409) 응답을 받는다
        assertThat(outcomes).filteredOn(outcome -> outcome.succeeded() && !outcome.result().alreadyUsed()).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode)
                .isSubsetOf("CREDIT409_1");
        assertThat(balanceOf(userId)).isEqualTo(3500);
        assertThat(usageRepository.count()).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void refundsOnceWhenRefundIsRequestedConcurrently() throws Exception {
        Long userId = userWithCredits(2000);
        creditService.useForAnalysis(userId, 1L, AnalysisCategory.CONFLICT);

        List<Outcome> outcomes = runConcurrently(index -> () -> {
            boolean refunded = creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패");
            return new UsageResult(null, refunded ? 1 : 0, false, 0, false);
        });

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(outcomes).filteredOn(outcome -> outcome.result().amount() == 1).hasSize(1);
        assertThat(balanceOf(userId)).isEqualTo(2000);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void givesFreeCharacterOnlyOncePerAccount() throws Exception {
        Long userId = userWithCredits(1000);

        // 서로 다른 녹음의 캐릭터를 동시에 요청한다. 무료는 1건, 500짜리는 잔액 1,000으로 2건까지만 가능하다
        List<Outcome> outcomes = runConcurrently(index ->
                () -> creditService.useForCharacter(userId, 200L + index));

        assertThat(outcomes).filteredOn(outcome -> outcome.succeeded() && outcome.result().freeUse()).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome.succeeded() && !outcome.result().freeUse()).hasSize(2);
        assertThat(balanceOf(userId)).isZero();
        assertThat(creditQueryService.getSummary(userId).characterFreeAvailable()).isFalse();
        assertLedgerMatchesBalance(userId);
    }

    // 지갑이 아직 없는 사용자에게 첫 요청이 동시에 몰려도 지갑은 하나만 생기고 모든 조정이 반영된다
    @Test
    void createsSingleWalletWhenFirstRequestsArriveConcurrently() throws Exception {
        Long userId = newUser();

        List<Outcome> outcomes = runConcurrently(index -> () -> {
            int balance = creditService.adjust(userId, 100, "동시 지급 " + index, ADMIN_ID);
            return new UsageResult(null, 0, false, balance, false);
        });

        assertThat(outcomes).allMatch(Outcome::succeeded);
        assertThat(walletRepository.count()).isEqualTo(1);
        assertThat(balanceOf(userId)).isEqualTo(THREADS * 100);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void keepsLedgerConsistentUnderMixedOperations() throws Exception {
        Long userId = userWithCredits(20_000);
        for (long recordingId = 1; recordingId <= THREADS; recordingId++) {
            creditService.useForAnalysis(userId, recordingId, AnalysisCategory.DAILY);
        }

        // 환급, 새 차감, 조정이 뒤섞여 들어온다
        List<Outcome> outcomes = runConcurrently(index -> () -> {
            switch (index % 3) {
                case 0 -> creditService.refund(UsagePurpose.ANALYSIS, index + 1L, "실패");
                case 1 -> creditService.useForAnalysis(userId, 500L + index, AnalysisCategory.CONFLICT);
                default -> creditService.adjust(userId, -100, "회수 " + index, ADMIN_ID);
            }
            return new UsageResult(null, 0, false, 0, false);
        });

        // 처리 순서에 따라 일부 차감 · 회수는 잔액 부족으로 거절될 수 있다. 그 밖의 실패는 없어야 한다
        assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
                .extracting(Outcome::errorCode)
                .isSubsetOf("CREDIT402_1", "CREDIT400_1");
        // 최종 금액은 순서에 따라 달라지므로 "잔액이 음수가 아니고 장부 합계와 같다"를 확인한다
        assertThat(balanceOf(userId)).isNotNegative();
        assertLedgerMatchesBalance(userId);
    }

    // 서로 다른 사용자의 요청은 서로를 막거나 교착시키지 않아야 한다 (런칭 직후 여러 사람이 동시에 결제하는 상황)
    @Test
    void differentUsersDoNotBlockOrDeadlockEachOther() throws Exception {
        List<Long> userIds = new ArrayList<>();
        for (int index = 0; index < THREADS; index++) {
            userIds.add(newUser());
        }

        for (int round = 0; round < 5; round++) {
            long base = 1_000L * (round + 1);
            List<Outcome> outcomes = runConcurrently(index -> () -> {
                Long userId = userIds.get(index);
                long recordingId = base + index * 10L;
                creditService.adjust(userId, 4000, "동시 지급", ADMIN_ID);
                // 첫 회차에는 무료, 이후에는 500
                creditService.useForCharacter(userId, recordingId);
                creditService.useForAnalysis(userId, recordingId, AnalysisCategory.CONFLICT);
                creditService.useForCharacter(userId, recordingId + 1);
                creditService.refund(UsagePurpose.CHARACTER, recordingId + 1, "이미지 생성 실패");
                // 이미 처리된 녹음을 다시 요청하는 경우와 없는 대상을 환급하는 경우도 섞는다
                creditService.useForAnalysis(userId, recordingId, AnalysisCategory.CONFLICT);
                creditService.refund(UsagePurpose.ANALYSIS, recordingId + 5, "없는 대상");
                return new UsageResult(null, 0, false, 0, false);
            });

            assertThat(outcomes).allMatch(Outcome::succeeded);
        }

        for (Long userId : userIds) {
            // 회차마다 +4,000 − 2,000(분석), 캐릭터는 첫 회차만 무료이고 이후 회차는 500
            assertThat(balanceOf(userId)).isEqualTo(5 * 2000 - 4 * 500);
            assertLedgerMatchesBalance(userId);
        }
    }

    private List<Outcome> runConcurrently(IntFunction<Callable<UsageResult>> taskFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (int index = 0; index < THREADS; index++) {
                Callable<UsageResult> task = taskFactory.apply(index);
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        return new Outcome(task.call(), null);
                    } catch (GeneralException exception) {
                        return new Outcome(null, exception.getErrorCode().getCode());
                    }
                }));
            }
            start.countDown();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            // 아직 도는 작업이 남은 채로 다음 테스트의 정리가 실행되지 않게 끝날 때까지 기다린다
            executor.shutdownNow();
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private Long userWithCredits(int credits) {
        Long userId = newUser();
        creditService.adjust(userId, credits, "테스트 지급", ADMIN_ID);
        return userId;
    }

    private Long newUser() {
        long sequence = SEQUENCE.incrementAndGet();
        return userRepository.saveAndFlush(
                User.createLocal("credit-race-" + sequence + "@example.com", "encoded-password", "동시" + sequence)
        ).getId();
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }

    private record Outcome(UsageResult result, String errorCode) {

        boolean succeeded() {
            return errorCode == null;
        }
    }
}
