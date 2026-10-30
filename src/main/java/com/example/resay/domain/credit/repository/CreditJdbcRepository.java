package com.example.resay.domain.credit.repository;

import com.example.resay.domain.credit.entity.CreditUsage;
import com.example.resay.domain.credit.entity.UsagePurpose;
import com.example.resay.domain.credit.entity.UsageStatus;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * 잔액과 사용 건을 바꾸는 SQL.
 *
 * 여기서는 JPA 엔티티를 쓰지 않고 SQL을 직접 실행한다. 실제 MySQL로 동시 요청을 시험하면서 확인한 이유는 다음과 같다.
 *
 * 1. MySQL 기본 격리수준(REPEATABLE READ)에서 일반 SELECT는 "그 트랜잭션이 처음 읽은 시점"의 데이터를 보여준다.
 *    행을 잠그려고 기다리는 동안 다른 요청이 잔액을 바꾸고 커밋해도, 잠금을 얻은 뒤의 일반 SELECT에는 옛 잔액이 나온다.
 *    Hibernate는 이미 읽어 둔 엔티티를 다시 채우지 않고, refresh도 잠금 없는 일반 SELECT로 실행해서 이 옛 값을 쓰게 된다.
 *    (동시 요청 12건이 잔액 3건분으로 모두 결제됐다) → 판단에 쓰는 값은 항상 잠금 읽기(FOR UPDATE)로 가져온다.
 *
 * 2. 중복으로 실패한 INSERT(INSERT IGNORE 포함)는 테이블 끝에 잠금을 남긴다. 그 상태로 같은 트랜잭션이 다시 INSERT하면
 *    같은 상황의 다른 트랜잭션과 서로를 기다리다 교착된다. → "넣어 보고 실패하면 다른 것을 넣는" 방식을 쓰지 않는다.
 *    무료 기회를 썼는지는 지갑 행에 함께 두어, 지갑을 잠글 때 잔액과 같이 읽는다.
 *
 * 3. 없는 행을 FOR UPDATE로 잠그면 인덱스의 빈 구간이 잠긴다. 여러 요청이 같은 구간을 잠근 채 각자 INSERT하면 교착된다.
 *    → 잠금 읽기는 행이 있는 것이 확실할 때만 쓴다.
 */
@Repository
@RequiredArgsConstructor
public class CreditJdbcRepository {

    private static final String USAGE_COLUMNS = "id, user_id, purpose, recording_id, amount, free_use, status";

    private final JdbcTemplate jdbcTemplate;

    /**
     * 사용자의 지갑 행을 잠그고 최신 상태를 돌려준다. 지갑이 없으면 잔액 0으로 만든다.
     * 같은 사용자의 다른 요청은 이 트랜잭션이 끝날 때까지 여기서 기다린다.
     */
    public WalletRow lockWallet(Long userId, LocalDateTime now) {
        // 있으면 그 행을, 없으면 새로 만든 행을 배타 잠금으로 잡는다. 기본키 중복이라 빈 구간은 잠기지 않는다
        jdbcTemplate.update(
                "INSERT INTO credit_wallets (user_id, balance, created_at, updated_at) VALUES (?, 0, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE user_id = user_id",
                userId, now, now
        );
        List<WalletRow> rows = jdbcTemplate.query(
                "SELECT balance, character_free_usage_id FROM credit_wallets WHERE user_id = ? FOR UPDATE",
                (resultSet, rowNumber) -> new WalletRow(
                        resultSet.getInt("balance"),
                        resultSet.getObject("character_free_usage_id", Long.class)
                ),
                userId
        );
        if (rows.size() != 1) {
            throw new IllegalStateException("크레딧 지갑을 찾을 수 없습니다.");
        }
        return rows.get(0);
    }

    /** 잔액에 더한다 (음수면 뺀다). lockWallet으로 잠근 뒤에만 호출한다. */
    public void changeBalance(Long userId, int delta, LocalDateTime now) {
        requireSingleRow(jdbcTemplate.update(
                "UPDATE credit_wallets SET balance = balance + ?, updated_at = ? WHERE user_id = ?",
                delta, now, userId
        ));
    }

    /**
     * 캐릭터 무료 기회를 쓴 사용 건을 지갑에 기록한다. null이면 기회를 되돌린다.
     * lockWallet으로 잠근 뒤에만 호출한다.
     */
    public void setCharacterFreeUsage(Long userId, Long usageId, LocalDateTime now) {
        requireSingleRow(jdbcTemplate.update(
                "UPDATE credit_wallets SET character_free_usage_id = ?, updated_at = ? WHERE user_id = ?",
                usageId, now, userId
        ));
    }

    /**
     * 사용 건을 추가한다.
     *
     * @return 새 사용 건의 ID
     * @throws DuplicateKeyException 같은 녹음 · 같은 용도에 유효한(USED) 사용 건이 이미 있을 때.
     *                               호출한 쪽은 이 예외를 잡아 계속 진행하지 말고 트랜잭션을 되돌려야 한다
     */
    public Long insertUsage(
            Long userId,
            UsagePurpose purpose,
            Long recordingId,
            int amount,
            boolean freeUse,
            LocalDateTime now
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO credit_usages "
                            + "(user_id, purpose, recording_id, amount, free_use, status, active_key, "
                            + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[]{"id"}
            );
            statement.setLong(1, userId);
            statement.setString(2, purpose.name());
            statement.setLong(3, recordingId);
            statement.setInt(4, amount);
            statement.setBoolean(5, freeUse);
            statement.setString(6, UsageStatus.USED.name());
            statement.setString(7, CreditUsage.activeKey(purpose, recordingId));
            statement.setObject(8, now);
            statement.setObject(9, now);
            return statement;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("사용 건의 ID를 받지 못했습니다.");
        }
        return key.longValue();
    }

    /**
     * 유효한 사용 건을 잠금 없이 찾는다.
     * 지갑을 잠근 뒤 그 트랜잭션의 첫 일반 조회로 실행하면 최신 데이터가 보인다. 호출한 쪽 트랜잭션이 그 전에 다른 조회를
     * 했다면 그 시점의 데이터가 보일 수 있다. 그 경우의 중복은 insertUsage의 유니크 제약이 마지막으로 막는다.
     */
    public Optional<UsageRow> findActiveUsage(UsagePurpose purpose, Long recordingId) {
        return queryUsage(
                "SELECT " + USAGE_COLUMNS + " FROM credit_usages WHERE active_key = ?",
                CreditUsage.activeKey(purpose, recordingId)
        );
    }

    /** 사용 건을 잠그고 최신 상태로 읽는다. 있는 것이 확실한 ID로만 호출한다. */
    public Optional<UsageRow> lockUsage(Long usageId) {
        return queryUsage("SELECT " + USAGE_COLUMNS + " FROM credit_usages WHERE id = ? FOR UPDATE", usageId);
    }

    /**
     * 사용 건을 환급 상태로 바꾼다. 유효할 때 채워 둔 active_key를 비워서 같은 녹음을 다시 쓸 수 있게 한다.
     *
     * @return 이번 호출로 바꿨으면 true (이미 환급된 건이면 false)
     */
    public boolean markRefunded(Long usageId, String reason, LocalDateTime now) {
        int updated = jdbcTemplate.update(
                "UPDATE credit_usages SET status = ?, active_key = NULL, refunded_at = ?, refund_reason = ?, "
                        + "updated_at = ? WHERE id = ? AND status = ?",
                UsageStatus.REFUNDED.name(), now, reason, now, usageId, UsageStatus.USED.name()
        );
        return updated == 1;
    }

    private void requireSingleRow(int updated) {
        if (updated != 1) {
            throw new IllegalStateException("크레딧 지갑을 변경하지 못했습니다.");
        }
    }

    private Optional<UsageRow> queryUsage(String sql, Object argument) {
        List<UsageRow> rows = jdbcTemplate.query(sql, CreditJdbcRepository::mapUsage, argument);
        return rows.stream().findFirst();
    }

    private static UsageRow mapUsage(ResultSet resultSet, int rowNumber) throws SQLException {
        return new UsageRow(
                resultSet.getLong("id"),
                resultSet.getLong("user_id"),
                UsagePurpose.valueOf(resultSet.getString("purpose")),
                resultSet.getLong("recording_id"),
                resultSet.getInt("amount"),
                resultSet.getBoolean("free_use"),
                UsageStatus.valueOf(resultSet.getString("status"))
        );
    }

    /**
     * @param characterFreeUsageId 캐릭터 무료 기회를 쓴 사용 건. 기회가 남아 있으면 null
     */
    public record WalletRow(
            int balance,
            Long characterFreeUsageId
    ) {

        public boolean hasCharacterFreeChance() {
            return characterFreeUsageId == null;
        }
    }

    public record UsageRow(
            Long id,
            Long userId,
            UsagePurpose purpose,
            Long recordingId,
            int amount,
            boolean freeUse,
            UsageStatus status
    ) {

        public boolean isUsed() {
            return status == UsageStatus.USED;
        }
    }
}
