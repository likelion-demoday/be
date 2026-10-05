package com.example.resay.domain.credit.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// 가격은 원가에 따라 바뀔 수 있어 코드가 아니라 설정(application.yml)에 둔다
@Validated
@ConfigurationProperties(prefix = "credit")
public record CreditProperties(
        @NotNull @Valid Price price
) {

    public record Price(
            @Min(1) int analysisDaily,
            @Min(1) int analysisConflict,
            @Min(1) int character
    ) {
    }
}
