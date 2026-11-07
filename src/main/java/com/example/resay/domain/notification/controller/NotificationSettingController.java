package com.example.resay.domain.notification.controller;

import com.example.resay.domain.notification.code.NotificationSuccessCode;
import com.example.resay.domain.notification.dto.NotificationSettingsResponseDto;
import com.example.resay.domain.notification.dto.UpdateNotificationEmailRequestDto;
import com.example.resay.domain.notification.dto.UpdateNotificationSettingsRequestDto;
import com.example.resay.domain.notification.service.NotificationSettingService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
@RequiredArgsConstructor
public class NotificationSettingController {

    private final NotificationSettingService notificationSettingService;

    @GetMapping("/notifications")
    public ApiResponse<NotificationSettingsResponseDto> getSettings(@CurrentUserId Long userId) {
        return ApiResponse.onSuccess(NotificationSuccessCode.GET_SETTINGS, notificationSettingService.getSettings(userId));
    }

    @PatchMapping("/notifications")
    public ApiResponse<NotificationSettingsResponseDto> updateSettings(
            @CurrentUserId Long userId,
            @RequestBody UpdateNotificationSettingsRequestDto request
    ) {
        return ApiResponse.onSuccess(
                NotificationSuccessCode.UPDATE_SETTINGS, notificationSettingService.updateSettings(userId, request));
    }

    // 온보딩 마지막 단계와 마이페이지에서 쓴다. 빈 값을 보내면 지운다
    @PutMapping("/notification-email")
    public ApiResponse<NotificationSettingsResponseDto> updateEmail(
            @CurrentUserId Long userId,
            @Valid @RequestBody UpdateNotificationEmailRequestDto request
    ) {
        return ApiResponse.onSuccess(
                NotificationSuccessCode.UPDATE_EMAIL, notificationSettingService.updateEmail(userId, request));
    }
}
