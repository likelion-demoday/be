package com.example.resay.domain.user.controller;

import com.example.resay.domain.user.code.UserSuccessCode;
import com.example.resay.domain.user.dto.UpdateUserRequestDto;
import com.example.resay.domain.user.dto.UserResponseDto;
import com.example.resay.domain.user.service.UserService;
import com.example.resay.global.apiPayload.ApiResponse;
import com.example.resay.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserResponseDto> getMyInfo(@CurrentUserId Long userId) {
        return ApiResponse.onSuccess(UserSuccessCode.GET_ME, userService.getMyInfo(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<UserResponseDto> updateMyInfo(
            @CurrentUserId Long userId,
            @Valid @RequestBody UpdateUserRequestDto request
    ) {
        return ApiResponse.onSuccess(UserSuccessCode.UPDATE_ME, userService.updateMyInfo(userId, request));
    }
}
