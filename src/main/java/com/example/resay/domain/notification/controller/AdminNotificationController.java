package com.example.resay.domain.notification.controller;

import com.example.resay.domain.notification.code.NotificationSuccessCode;
import com.example.resay.domain.notification.dto.ServiceAnnouncementRequestDto;
import com.example.resay.domain.notification.dto.ServiceAnnouncementResponseDto;
import com.example.resay.domain.notification.service.ServiceAnnouncementService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// /api/v1/admin/** 은 ADMIN 권한이 있어야 접근할 수 있다 (SecurityConfig)
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final ServiceAnnouncementService serviceAnnouncementService;

    // 메일을 다 보낸 뒤에 응답한다 (한 통에 약 0.6초, 80명이면 약 50초)
    @PostMapping("/service")
    public ApiResponse<ServiceAnnouncementResponseDto> sendServiceAnnouncement(
            @CurrentUserId Long adminId,
            @Valid @RequestBody ServiceAnnouncementRequestDto request
    ) {
        log.info("서비스 알림 발송 요청: adminId={}", adminId);
        return ApiResponse.onSuccess(
                NotificationSuccessCode.SEND_SERVICE_ANNOUNCEMENT, serviceAnnouncementService.send(request));
    }
}
