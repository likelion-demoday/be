package com.example.resay.domain.credit.dto;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import com.example.resay.domain.credit.entity.CreditLedgerType;
import com.example.resay.domain.credit.entity.UsagePurpose;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Slice;

public record CreditHistoryResponseDto(
        List<Item> items,
        int page,
        int size,
        boolean hasNext
) {

    public record Item(
            Long id,
            CreditLedgerType type,
            // 잔액에 더해진 값 (늘면 양수, 줄면 음수)
            int amount,
            int balanceAfter,
            // 사용 · 환급일 때만 값이 있다
            UsagePurpose purpose,
            Long recordingId,
            LocalDateTime createdAt
    ) {

        static Item from(CreditLedgerEntry entry) {
            return new Item(
                    entry.getId(),
                    entry.getType(),
                    entry.getAmount(),
                    entry.getBalanceAfter(),
                    entry.getPurpose(),
                    entry.getRecordingId(),
                    entry.getCreatedAt()
            );
        }
    }

    public static CreditHistoryResponseDto from(Slice<CreditLedgerEntry> slice) {
        return new CreditHistoryResponseDto(
                slice.getContent().stream().map(Item::from).toList(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext()
        );
    }
}
