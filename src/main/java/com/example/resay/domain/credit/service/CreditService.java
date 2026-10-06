package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.code.CreditErrorCode;
import com.example.resay.domain.credit.config.CreditProperties;
import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.model.AnalysisCategory;
import com.example.resay.domain.credit.model.UsageResult;
import com.example.resay.domain.credit.repository.CreditJdbcRepository;
import com.example.resay.domain.credit.repository.CreditJdbcRepository.UsageRow;
import com.example.resay.domain.credit.repository.CreditJdbcRepository.WalletRow;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.user.code.UserErrorCode;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 크레딧 차감 · 환급 · 조정.
 *
 * 잔액을 바꾸는 모든 작업은 같은 순서를 따른다.
 *  1. 그 사용자의 지갑 행을 잠그고 최신 잔액을 읽는다 (같은 사용자의 동시 요청은 여기서 한 줄로 선다)
 *  2. 잠근 상태에서 조건을 확인하고 잔액을 바꾼다
 *  3. 같은 트랜잭션에서 장부에 한 줄을 추가한다
 *
 * 호출한 쪽에 트랜잭션이 있으면 거기에 합류한다. 그래서 "차감 + 호출한 쪽의 상태 변경"을 한 번에 커밋할 수 있다.
 * 합류해서 쓸 때는 그 트랜잭션에서 다른 조회보다 먼저 호출하는 것이 좋다 (이유는 CreditJdbcRepository 설명 참고).
 */
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(CreditProperties.class)
public class CreditService {

    // 입력 실수나 버그로 잔액이 비정상적으로 커지는 것을 막는 상한
    private static final int MAX_BALANCE = 100_000_000;

    private final CreditJdbcRepository creditJdbcRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final CreditProperties creditProperties;

    /**
     * 분석 비용을 차감한다. 이미 차감된 녹음이면 다시 차감하지 않고 그 결과를 돌려준다.
     * 잔액이 부족하면 INSUFFICIENT_CREDIT 예외를 던지고 아무것도 바꾸지 않는다.
     */
    @Transactional
    public UsageResult useForAnalysis(Long userId, Long recordingId, AnalysisCategory category) {
        int price = switch (category) {
            case DAILY -> creditProperties.price().analysisDaily();
            case CONFLICT -> creditProperties.price().analysisConflict();
        };
        return use(userId, UsagePurpose.ANALYSIS, recordingId, price, false);
    }

    /**
     * 캐릭터 생성 비용을 차감한다. 계정의 무료 기회가 남아 있으면 차감 없이 그 기회를 쓴다.
     * 캐릭터는 분석 1건(녹음 1건)에 한 세트이며 화자 수와 무관하다.
     */
    @Transactional
    public UsageResult useForCharacter(Long userId, Long recordingId) {
        return use(userId, UsagePurpose.CHARACTER, recordingId, creditProperties.price().character(), true);
    }

    /**
     * 사용한 크레딧을 되돌린다. 무료 기회를 쓴 건이었다면 기회가 되살아난다.
     * 유효한 사용 건이 없거나 이미 환급됐으면 아무 일도 하지 않으므로, 실패 처리가 겹쳐 여러 번 호출돼도 안전하다.
     *
     * @return 이번 호출로 환급했으면 true
     */
    @Transactional
    public boolean refund(UsagePurpose purpose, Long recordingId, String reason) {
        // 누구의 사용 건인지 알아야 지갑을 잠글 수 있다. 여기서 읽은 상태는 믿지 않고 잠근 뒤 다시 읽는다
        Optional<UsageRow> found = creditJdbcRepository.findActiveUsage(purpose, recordingId);
        if (found.isEmpty()) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        WalletRow wallet = creditJdbcRepository.lockWallet(found.get().userId(), now);
        UsageRow usage = creditJdbcRepository.lockUsage(found.get().id())
                .orElseThrow(() -> new IllegalStateException("사용 건을 찾을 수 없습니다."));
        // 지갑을 기다리는 사이 다른 요청이 먼저 환급했을 수 있다
        if (!usage.isUsed() || !creditJdbcRepository.markRefunded(usage.id(), truncate(reason), now)) {
            return false;
        }

        if (usage.id().equals(wallet.characterFreeUsageId())) {
            creditJdbcRepository.setCharacterFreeUsage(usage.userId(), null, now);
        }
        if (usage.amount() > 0) {
            creditJdbcRepository.changeBalance(usage.userId(), usage.amount(), now);
        }
        ledgerRepository.save(CreditLedgerEntry.useRefund(
                usage.userId(), usage.id(), usage.purpose(), usage.recordingId(),
                usage.amount(), wallet.balance() + usage.amount()));
        return true;
    }

    /**
     * 운영자가 크레딧을 직접 더하거나 뺀다 (일부 사용한 충전의 수동 환불, 보상 지급 등).
     *
     * @param amount 더할 값. 음수면 뺀다. 잔액보다 많이 뺄 수는 없다
     * @return 조정 후 잔액
     */
    @Transactional
    public int adjust(Long userId, int amount, String memo, Long adminId) {
        if (amount == 0) {
            throw new GeneralException(CreditErrorCode.INVALID_ADJUSTMENT);
        }
        if (!userRepository.existsById(userId)) {
            throw new GeneralException(UserErrorCode.USER_NOT_FOUND);
        }

        LocalDateTime now = LocalDateTime.now();
        int balance = creditJdbcRepository.lockWallet(userId, now).balance();
        long adjusted = (long) balance + amount;
        if (adjusted < 0 || adjusted > MAX_BALANCE) {
            throw new GeneralException(CreditErrorCode.INVALID_ADJUSTMENT, Map.of("balance", balance));
        }

        creditJdbcRepository.changeBalance(userId, amount, now);
        ledgerRepository.save(CreditLedgerEntry.adjust(userId, amount, (int) adjusted, memo, adminId));
        return (int) adjusted;
    }

    private UsageResult use(Long userId, UsagePurpose purpose, Long recordingId, int price, boolean freeEligible) {
        LocalDateTime now = LocalDateTime.now();
        WalletRow wallet = creditJdbcRepository.lockWallet(userId, now);
        int balance = wallet.balance();

        // 이미 차감된 녹음이면 잔액과 무관하게 그 결과를 돌려준다 (연타한 두 번째 요청이 오류로 보이지 않게)
        Optional<UsageRow> existing = creditJdbcRepository.findActiveUsage(purpose, recordingId);
        if (existing.isPresent()) {
            // 호출하는 쪽이 본인 녹음인지 확인하므로 다른 사용자의 건이 나오는 일은 없어야 한다
            if (!existing.get().userId().equals(userId)) {
                throw new GeneralException(CreditErrorCode.USAGE_CONFLICT);
            }
            return new UsageResult(
                    existing.get().id(), existing.get().amount(), existing.get().freeUse(), balance, true);
        }

        boolean free = freeEligible && wallet.hasCharacterFreeChance();
        int amount = free ? 0 : price;
        if (balance < amount) {
            throw new GeneralException(
                    CreditErrorCode.INSUFFICIENT_CREDIT,
                    Map.of("required", amount, "balance", balance, "shortage", amount - balance)
            );
        }

        Long usageId;
        try {
            usageId = creditJdbcRepository.insertUsage(userId, purpose, recordingId, amount, free, now);
        } catch (DuplicateKeyException exception) {
            // 위의 확인에는 안 보였지만 유효한 사용 건이 이미 있는 경우 (같은 녹음의 요청이 동시에 처리됨).
            // 예외로 트랜잭션 전체를 되돌린다. 다시 요청하면 "이미 차감됨" 결과를 받는다
            throw new GeneralException(CreditErrorCode.USAGE_CONFLICT);
        }

        if (free) {
            creditJdbcRepository.setCharacterFreeUsage(userId, usageId, now);
        } else {
            creditJdbcRepository.changeBalance(userId, -amount, now);
        }
        ledgerRepository.save(CreditLedgerEntry.use(userId, usageId, purpose, recordingId, amount, balance - amount));
        return new UsageResult(usageId, amount, free, balance - amount, false);
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= CreditUsage.REFUND_REASON_MAX_LENGTH) {
            return reason;
        }
        return reason.substring(0, CreditUsage.REFUND_REASON_MAX_LENGTH);
    }
}
