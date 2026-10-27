package com.example.resay.domain.credit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 가격은 원가에 따라 바뀔 수 있어 코드가 아니라 설정(application.yml)에 둔다.
 *
 * @param characterStallTimeout 캐릭터 생성을 요청한 뒤 이 시간 동안 이미지 진행이 없으면 멈춘 것으로 보고 환급한다
 *                              (CreditRefundReconciler). 정상 생성 중인 건을 환급하지 않도록 이미지 한 장의 최대 생성 시간과
 *                              작업 대기열에서 기다리는 시간보다 길어야 한다
 */
@Validated
@ConfigurationProperties(prefix = "credit")
public record CreditProperties(
        @NotNull @Valid Price price,
        @NotNull Duration characterStallTimeout
) {

    public record Price(
            @Min(1) int analysisDaily,
            @Min(1) int analysisConflict,
            @Min(1) int character
    ) {
    }

    // 0이나 음수면 방금 요청한 건까지 멈춘 것으로 보고 전부 환급한다
    @AssertTrue(message = "credit.character-stall-timeout은 0보다 커야 합니다.")
    public boolean isCharacterStallTimeoutPositive() {
        return characterStallTimeout == null
                || !(characterStallTimeout.isNegative() || characterStallTimeout.isZero());
    }
}
