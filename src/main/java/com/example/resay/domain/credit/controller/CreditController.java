package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.code.CreditSuccessCode;
import com.example.resay.domain.credit.dto.CreditHistoryResponseDto;
import com.example.resay.domain.credit.dto.CreditPriceResponseDto;
import com.example.resay.domain.credit.dto.CreditSummaryResponseDto;
import com.example.resay.domain.credit.service.CreditQueryService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/credits")
@RequiredArgsConstructor
public class CreditController {

    private final CreditQueryService creditQueryService;

    @GetMapping("/me")
    public ApiResponse<CreditSummaryResponseDto> getMySummary(@CurrentUserId Long userId) {
        return ApiResponse.onSuccess(CreditSuccessCode.GET_SUMMARY, creditQueryService.getSummary(userId));
    }

    // 최근 내역부터 내려준다
    @GetMapping("/history")
    public ApiResponse<CreditHistoryResponseDto> getMyHistory(
            @CurrentUserId Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.onSuccess(
                CreditSuccessCode.GET_HISTORY, creditQueryService.getHistory(userId, page, size));
    }

    @GetMapping("/prices")
    public ApiResponse<CreditPriceResponseDto> getPrices() {
        return ApiResponse.onSuccess(CreditSuccessCode.GET_PRICES, creditQueryService.getPrices());
    }
}
