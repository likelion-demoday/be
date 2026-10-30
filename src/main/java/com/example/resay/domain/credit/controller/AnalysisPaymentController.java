package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.code.CreditSuccessCode;
import com.example.resay.domain.credit.dto.AnalysisPaymentResponseDto;
import com.example.resay.domain.credit.service.AnalysisPaymentService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class AnalysisPaymentController {

    private final AnalysisPaymentService analysisPaymentService;

    // 유형을 선택한 녹음의 분석을 크레딧으로 결제한다. 결제되면 전사 · 분석이 시작된다
    @PostMapping("/{recordingId}/payment")
    public ApiResponse<AnalysisPaymentResponseDto> pay(
            @CurrentUserId Long userId,
            @PathVariable Long recordingId
    ) {
        return ApiResponse.onSuccess(CreditSuccessCode.PAY_ANALYSIS, analysisPaymentService.pay(userId, recordingId));
    }
}
