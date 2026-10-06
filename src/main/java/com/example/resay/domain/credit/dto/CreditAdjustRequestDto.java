package com.example.resay.domain.credit.dto;

import com.example.resay.domain.credit.entity.CreditLedgerEntry;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreditAdjustRequestDto(
        @NotNull(message = "대상 사용자를 입력해 주세요.")
        Long userId,
        // 더할 값. 음수면 뺀다. 한 번에 바꿀 수 있는 크기를 제한해 입력 실수(0을 하나 더 붙이는 등)의 피해를 줄인다
        @NotNull(message = "조정할 크레딧을 입력해 주세요.")
        @Min(value = -100_000, message = "한 번에 100,000 크레딧까지 조정할 수 있습니다.")
        @Max(value = 100_000, message = "한 번에 100,000 크레딧까지 조정할 수 있습니다.")
        Integer amount,
        @NotBlank(message = "조정 사유를 입력해 주세요.")
        @Size(max = CreditLedgerEntry.MEMO_MAX_LENGTH, message = "조정 사유는 200자 이하여야 합니다.")
        String memo
) {

    public CreditAdjustRequestDto {
        memo = memo == null ? null : memo.trim();
    }
}
