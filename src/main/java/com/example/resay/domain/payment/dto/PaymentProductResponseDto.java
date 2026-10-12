package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.config.PaymentProperties;
import java.util.List;

public record PaymentProductResponseDto(
        List<Item> products
) {

    public record Item(
            String code,
            // 결제 금액(원)
            int amount,
            // 지급 크레딧
            int credits
    ) {
    }

    public static PaymentProductResponseDto from(List<PaymentProperties.Product> products) {
        return new PaymentProductResponseDto(products.stream()
                .map(product -> new Item(product.code(), product.amount(), product.credits()))
                .toList());
    }
}
