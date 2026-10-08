package com.example.resay.domain.payment.dto;

import com.example.resay.domain.payment.entity.Payment;
import java.util.List;
import org.springframework.data.domain.Slice;

public record PaymentHistoryResponseDto(
        List<PaymentResponseDto> items,
        int page,
        int size,
        boolean hasNext
) {

    public static PaymentHistoryResponseDto from(Slice<Payment> slice) {
        return new PaymentHistoryResponseDto(
                slice.getContent().stream().map(PaymentResponseDto::from).toList(),
                slice.getNumber(),
                slice.getSize(),
                slice.hasNext()
        );
    }
}
