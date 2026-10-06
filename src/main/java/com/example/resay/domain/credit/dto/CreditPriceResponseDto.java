package com.example.resay.domain.credit.dto;

import com.example.resay.domain.credit.config.CreditProperties;

public record CreditPriceResponseDto(
        Analysis analysis,
        int character
) {

    public record Analysis(
            int daily,
            int conflict
    ) {
    }

    public static CreditPriceResponseDto from(CreditProperties.Price price) {
        return new CreditPriceResponseDto(
                new Analysis(price.analysisDaily(), price.analysisConflict()),
                price.character()
        );
    }
}
