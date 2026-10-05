package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.entity.UsageStatus;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.model.UsageResult;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditUsageRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

// 잠금과 커밋 이후의 상태를 확인해야 해서 테스트 트랜잭션으로 감싸지 않는다. 만든 데이터는 테스트마다 지운다
@SpringBootTest
class CreditServiceTest {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Long ADMIN_ID = 999_999L;

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

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        ledgerRepository.deleteAllInBatch();
        usageRepository.deleteAllInBatch();
        walletRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void deductsAnalysisPriceByCategory() {
        Long userId = userWithCredits(5000);

        UsageResult daily = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
        UsageResult conflict = creditService.useForAnalysis(userId, 2L, AnalysisCategory.CONFLICT);

        assertThat(daily.amount()).isEqualTo(1500);
        assertThat(daily.balance()).isEqualTo(3500);
        assertThat(daily.alreadyUsed()).isFalse();
        assertThat(conflict.amount()).isEqualTo(2000);
        assertThat(conflict.balance()).isEqualTo(1500);
        assertThat(balanceOf(userId)).isEqualTo(1500);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void recordsUsageAndLedgerEntry() {
        Long userId = userWithCredits(2000);

        UsageResult result = creditService.useForAnalysis(userId, 7L, AnalysisCategory.DAILY);

        CreditUsage usage = usageRepository.findById(result.usageId()).orElseThrow();
        assertThat(usage.getUserId()).isEqualTo(userId);
        assertThat(usage.getPurpose()).isEqualTo(UsagePurpose.ANALYSIS);
        assertThat(usage.getRecordingId()).isEqualTo(7L);
        assertThat(usage.getAmount()).isEqualTo(1500);
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.USED);

        CreditLedgerEntry entry = lastLedgerEntry(userId);
        assertThat(entry.getType()).isEqualTo(CreditLedgerType.USE);
        assertThat(entry.getAmount()).isEqualTo(-1500);
        assertThat(entry.getBalanceAfter()).isEqualTo(500);
        assertThat(entry.getUsageId()).isEqualTo(usage.getId());
        assertThat(entry.getPurpose()).isEqualTo(UsagePurpose.ANALYSIS);
        assertThat(entry.getRecordingId()).isEqualTo(7L);
    }

    @Test
    void rejectsUseWhenBalanceIsInsufficientAndChangesNothing() {
        Long userId = userWithCredits(1400);

        GeneralException exception = catchThrowableOfType(
                GeneralException.class,
                () -> creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT402_1");
        assertThat(exception.getDetail()).isEqualTo(Map.of("required", 1500, "balance", 1400, "shortage", 100));
        assertThat(balanceOf(userId)).isEqualTo(1400);
        assertThat(usageRepository.count()).isZero();
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId)).hasSize(1);
    }

    @Test
    void allowsUseWhenBalanceEqualsPrice() {
        Long userId = userWithCredits(1500);

        creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        assertThat(balanceOf(userId)).isZero();
    }

    @Test
    void rejectsUseForUserWhoNeverHadCredits() {
        Long userId = newUser();

        assertThatThrownBy(() -> creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT402_1"));
        assertThat(balanceOf(userId)).isZero();
    }

    @Test
    void doesNotDeductTwiceForSameRecording() {
        Long userId = userWithCredits(5000);
        UsageResult first = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        UsageResult second = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        assertThat(second.alreadyUsed()).isTrue();
        assertThat(second.usageId()).isEqualTo(first.usageId());
        assertThat(second.amount()).isEqualTo(1500);
        assertThat(second.balance()).isEqualTo(3500);
        assertThat(balanceOf(userId)).isEqualTo(3500);
        assertThat(usageRepository.count()).isEqualTo(1);
    }

    // 이미 차감된 녹음이면 잔액이 모자라도 "이미 처리됨"으로 답한다 (연타했을 때 두 번째 요청이 오류로 보이지 않게)
    @Test
    void returnsAlreadyUsedEvenIfBalanceIsNowInsufficient() {
        Long userId = userWithCredits(1500);
        creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        UsageResult second = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        assertThat(second.alreadyUsed()).isTrue();
        assertThat(balanceOf(userId)).isZero();
    }

    @Test
    void rejectsUseOfRecordingAlreadyUsedByAnotherUser() {
        Long owner = userWithCredits(5000);
        Long other = userWithCredits(5000);
        creditService.useForAnalysis(owner, 1L, AnalysisCategory.DAILY);

        assertThatThrownBy(() -> creditService.useForAnalysis(other, 1L, AnalysisCategory.DAILY))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT409_1"));
        assertThat(balanceOf(other)).isEqualTo(5000);
    }

    @Test
    void refundReturnsCreditsAndKeepsUsageAsRefunded() {
        Long userId = userWithCredits(2000);
        UsageResult used = creditService.useForAnalysis(userId, 1L, AnalysisCategory.CONFLICT);

        boolean refunded = creditService.refund(UsagePurpose.ANALYSIS, 1L, "전사 실패");

        assertThat(refunded).isTrue();
        assertThat(balanceOf(userId)).isEqualTo(2000);
        CreditUsage usage = usageRepository.findById(used.usageId()).orElseThrow();
        assertThat(usage.getStatus()).isEqualTo(UsageStatus.REFUNDED);
        assertThat(usage.getRefundReason()).isEqualTo("전사 실패");
        assertThat(usage.getRefundedAt()).isNotNull();
        CreditLedgerEntry entry = lastLedgerEntry(userId);
        assertThat(entry.getType()).isEqualTo(CreditLedgerType.USE_REFUND);
        assertThat(entry.getAmount()).isEqualTo(2000);
        assertThat(entry.getBalanceAfter()).isEqualTo(2000);
        assertThat(entry.getUsageId()).isEqualTo(usage.getId());
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void refundHappensOnlyOnce() {
        Long userId = userWithCredits(2000);
        creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        assertThat(creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패")).isTrue();
        assertThat(creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패")).isFalse();
        assertThat(creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패")).isFalse();

        assertThat(balanceOf(userId)).isEqualTo(2000);
        assertLedgerMatchesBalance(userId);
    }

    // 결제 전에 실패한 녹음처럼 차감한 적이 없는 대상의 환급 요청은 아무 일도 하지 않는다
    @Test
    void refundOfUnknownTargetDoesNothing() {
        Long userId = userWithCredits(2000);

        assertThat(creditService.refund(UsagePurpose.ANALYSIS, 12345L, "실패")).isFalse();

        assertThat(balanceOf(userId)).isEqualTo(2000);
    }

    @Test
    void refundDoesNotTouchOtherPurposeOfSameRecording() {
        Long userId = userWithCredits(5000);
        creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
        creditService.useForCharacter(userId, 1L);
        creditService.useForCharacter(userId, 2L);

        assertThat(creditService.refund(UsagePurpose.CHARACTER, 2L, "이미지 생성 실패")).isTrue();

        assertThat(usageRepository.findByActiveKey(CreditUsage.activeKey(UsagePurpose.ANALYSIS, 1L))).isPresent();
        assertThat(usageRepository.findByActiveKey(CreditUsage.activeKey(UsagePurpose.CHARACTER, 1L))).isPresent();
        assertThat(balanceOf(userId)).isEqualTo(3500);
    }

    @Test
    void canUseSameRecordingAgainAfterRefund() {
        Long userId = userWithCredits(2000);
        UsageResult first = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
        creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패");

        UsageResult second = creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);

        assertThat(second.alreadyUsed()).isFalse();
        assertThat(second.usageId()).isNotEqualTo(first.usageId());
        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId))
                .extracting(CreditLedgerEntry::getType)
                .containsExactly(
                        CreditLedgerType.ADJUST,
                        CreditLedgerType.USE,
                        CreditLedgerType.USE_REFUND,
                        CreditLedgerType.USE
                );
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void firstCharacterIsFreeEvenWithoutCredits() {
        Long userId = newUser();

        UsageResult result = creditService.useForCharacter(userId, 1L);

        assertThat(result.freeUse()).isTrue();
        assertThat(result.amount()).isZero();
        assertThat(result.balance()).isZero();
        assertThat(creditQueryService.getSummary(userId).characterFreeAvailable()).isFalse();
        CreditLedgerEntry entry = lastLedgerEntry(userId);
        assertThat(entry.getType()).isEqualTo(CreditLedgerType.USE);
        assertThat(entry.getAmount()).isZero();
        assertThat(entry.getPurpose()).isEqualTo(UsagePurpose.CHARACTER);
    }

    @Test
    void secondCharacterCostsCredits() {
        Long userId = userWithCredits(1000);
        creditService.useForCharacter(userId, 1L);

        UsageResult second = creditService.useForCharacter(userId, 2L);

        assertThat(second.freeUse()).isFalse();
        assertThat(second.amount()).isEqualTo(500);
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    @Test
    void secondCharacterIsRejectedWithoutCredits() {
        Long userId = newUser();
        creditService.useForCharacter(userId, 1L);

        assertThatThrownBy(() -> creditService.useForCharacter(userId, 2L))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT402_1"));
    }

    @Test
    void freeChanceIsPerAccount() {
        Long first = newUser();
        Long second = newUser();
        creditService.useForCharacter(first, 1L);

        assertThat(creditService.useForCharacter(second, 2L).freeUse()).isTrue();
    }

    @Test
    void freeChanceComesBackWhenFreeCharacterFails() {
        Long userId = newUser();
        creditService.useForCharacter(userId, 1L);

        assertThat(creditService.refund(UsagePurpose.CHARACTER, 1L, "이미지 생성 실패")).isTrue();

        assertThat(creditQueryService.getSummary(userId).characterFreeAvailable()).isTrue();
        assertThat(creditService.useForCharacter(userId, 1L).freeUse()).isTrue();
        assertThat(balanceOf(userId)).isZero();
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void paidCharacterRefundReturnsCreditsButNotFreeChance() {
        Long userId = userWithCredits(500);
        creditService.useForCharacter(userId, 1L);
        creditService.useForCharacter(userId, 2L);

        creditService.refund(UsagePurpose.CHARACTER, 2L, "이미지 생성 실패");

        assertThat(balanceOf(userId)).isEqualTo(500);
        assertThat(creditQueryService.getSummary(userId).characterFreeAvailable()).isFalse();
    }

    @Test
    void sameCharacterRequestTwiceIsNotChargedOrCountedTwice() {
        Long userId = userWithCredits(1000);
        creditService.useForCharacter(userId, 1L);
        creditService.useForCharacter(userId, 2L);

        UsageResult again = creditService.useForCharacter(userId, 2L);

        assertThat(again.alreadyUsed()).isTrue();
        assertThat(balanceOf(userId)).isEqualTo(500);
    }

    @Test
    void adjustAddsAndSubtractsCredits() {
        Long userId = newUser();

        assertThat(creditService.adjust(userId, 3000, "테스트 지급", ADMIN_ID)).isEqualTo(3000);
        assertThat(creditService.adjust(userId, -1200, "수동 환불 처리", ADMIN_ID)).isEqualTo(1800);

        CreditLedgerEntry entry = lastLedgerEntry(userId);
        assertThat(entry.getType()).isEqualTo(CreditLedgerType.ADJUST);
        assertThat(entry.getAmount()).isEqualTo(-1200);
        assertThat(entry.getBalanceAfter()).isEqualTo(1800);
        assertThat(entry.getMemo()).isEqualTo("수동 환불 처리");
        assertThat(entry.getCreatedBy()).isEqualTo(ADMIN_ID);
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void adjustCannotTakeMoreThanBalance() {
        Long userId = userWithCredits(1000);

        GeneralException exception = catchThrowableOfType(
                GeneralException.class,
                () -> creditService.adjust(userId, -1001, "회수", ADMIN_ID));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT400_1");
        assertThat(exception.getDetail()).isEqualTo(Map.of("balance", 1000));
        assertThat(balanceOf(userId)).isEqualTo(1000);
    }

    @Test
    void adjustRejectsZeroAndUnknownUser() {
        Long userId = newUser();

        assertThatThrownBy(() -> creditService.adjust(userId, 0, "의미 없음", ADMIN_ID))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT400_1"));
        assertThatThrownBy(() -> creditService.adjust(987_654_321L, 100, "없는 사용자", ADMIN_ID))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                        assertThat(exception.getErrorCode().getCode()).isEqualTo("USER404_1"));
        assertThat(walletRepository.count()).isZero();
    }

    // 호출한 쪽의 트랜잭션에 합류하므로, 호출한 쪽이 실패하면 차감도 함께 취소된다 (분석 결제에서 쓰는 성질)
    @Test
    void useIsRolledBackTogetherWithCallerTransaction() {
        Long userId = userWithCredits(2000);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
            throw new IllegalStateException("호출한 쪽의 실패");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(balanceOf(userId)).isEqualTo(2000);
        assertThat(usageRepository.count()).isZero();
        assertLedgerMatchesBalance(userId);
    }

    @Test
    void severalOperationsInOneTransactionSeeEachOther() {
        Long userId = userWithCredits(4000);

        transactionTemplate.executeWithoutResult(status -> {
            creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
            creditService.adjust(userId, -500, "회수", ADMIN_ID);
            creditService.useForAnalysis(userId, 2L, AnalysisCategory.CONFLICT);
            creditService.refund(UsagePurpose.ANALYSIS, 1L, "실패");
        });

        assertThat(balanceOf(userId)).isEqualTo(1500);
        assertThat(ledgerRepository.findByUserIdOrderByIdAsc(userId))
                .extracting(CreditLedgerEntry::getBalanceAfter)
                .containsExactly(4000, 2500, 2000, 0, 1500);
        assertLedgerMatchesBalance(userId);
    }

    // 트랜잭션 안에서 잔액을 먼저 읽어 둔 뒤 다른 요청이 잔액을 줄여도, 차감할 때는 잠금을 건 최신 잔액으로 판단해야 한다.
    // (MySQL 기본 격리수준에서는 먼저 읽은 시점의 값이 계속 보이므로, 잠금 없이 읽은 값을 믿으면 없는 돈을 쓰게 된다)
    @Test
    void usesLatestBalanceEvenIfTransactionReadAnOlderOne() {
        Long userId = userWithCredits(1500);

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            assertThat(creditQueryService.getSummary(userId).balance()).isEqualTo(1500);
            CompletableFuture.runAsync(() -> creditService.adjust(userId, -1000, "다른 요청의 회수", ADMIN_ID)).join();

            creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
        })).isInstanceOfSatisfying(GeneralException.class, exception ->
                assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT402_1"));

        assertThat(balanceOf(userId)).isEqualTo(500);
        assertLedgerMatchesBalance(userId);
    }

    // 호출한 쪽 트랜잭션이 먼저 조회를 해 둔 상태에서 같은 녹음이 다른 요청으로 이미 차감된 경우.
    // DB에 따라 "이미 차감됨"으로 답하거나 충돌로 거절하지만, 어느 쪽이든 두 번 차감되지는 않는다
    @Test
    void neverDeductsTwiceEvenIfTransactionCannotSeeTheOtherRequest() {
        Long userId = userWithCredits(5000);

        try {
            UsageResult result = transactionTemplate.execute(status -> {
                creditQueryService.getSummary(userId);
                CompletableFuture.runAsync(
                        () -> creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY)).join();

                return creditService.useForAnalysis(userId, 1L, AnalysisCategory.DAILY);
            });
            assertThat(result.alreadyUsed()).isTrue();
        } catch (GeneralException exception) {
            assertThat(exception.getErrorCode().getCode()).isEqualTo("CREDIT409_1");
        }

        assertThat(balanceOf(userId)).isEqualTo(3500);
        assertThat(usageRepository.count()).isEqualTo(1);
        assertLedgerMatchesBalance(userId);
    }

    private Long userWithCredits(int credits) {
        Long userId = newUser();
        creditService.adjust(userId, credits, "테스트 지급", ADMIN_ID);
        return userId;
    }

    private Long newUser() {
        long sequence = SEQUENCE.incrementAndGet();
        return userRepository.saveAndFlush(
                User.createLocal("credit-" + sequence + "@example.com", "encoded-password", "크레딧" + sequence)
        ).getId();
    }

    private int balanceOf(Long userId) {
        return creditQueryService.getSummary(userId).balance();
    }

    private CreditLedgerEntry lastLedgerEntry(Long userId) {
        List<CreditLedgerEntry> entries = ledgerRepository.findByUserIdOrderByIdAsc(userId);
        return entries.get(entries.size() - 1);
    }

    private void assertLedgerMatchesBalance(Long userId) {
        assertThat(ledgerRepository.sumAmountByUserId(userId)).isEqualTo(balanceOf(userId));
    }
}
