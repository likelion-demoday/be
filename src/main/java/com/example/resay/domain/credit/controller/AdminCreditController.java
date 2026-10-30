package com.example.resay.domain.credit.controller;

import com.example.resay.domain.credit.code.CreditSuccessCode;
import com.example.resay.domain.credit.dto.CreditAdjustRequestDto;
import com.example.resay.domain.credit.dto.CreditAdjustResponseDto;
import com.example.resay.domain.credit.service.CreditService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/v1/admin/** 은 ADMIN 권한이 있어야 접근할 수 있다 (SecurityConfig)
@RestController
@RequestMapping("/api/v1/admin/credits")
@RequiredArgsConstructor
public class AdminCreditController {

    private final CreditService creditService;

    @PostMapping("/adjust")
    public ApiResponse<CreditAdjustResponseDto> adjust(
            @CurrentUserId Long adminId,
            @Valid @RequestBody CreditAdjustRequestDto request
    ) {
        int balance = creditService.adjust(request.userId(), request.amount(), request.memo(), adminId);
        return ApiResponse.onSuccess(
                CreditSuccessCode.ADJUST, new CreditAdjustResponseDto(request.userId(), balance));
    }
}
