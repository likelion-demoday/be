package com.example.resay.domain.auth.controller;

import com.example.resay.domain.auth.code.AuthSuccessCode;
import com.example.resay.domain.auth.dto.GoogleLoginRequestDto;
import com.example.resay.domain.auth.dto.KakaoLoginRequestDto;
import com.example.resay.domain.auth.dto.LoginRequestDto;
import com.example.resay.domain.auth.dto.RefreshTokenRequestDto;
import com.example.resay.domain.auth.dto.SignupRequestDto;
import com.example.resay.domain.auth.dto.TokenResponseDto;
import com.example.resay.domain.auth.service.AuthService;
import com.example.resay.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponseDto>> signup(
            @Valid @RequestBody SignupRequestDto request
    ) {
        TokenResponseDto response = authService.signup(request);
        return ResponseEntity.status(AuthSuccessCode.SIGNUP.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.SIGNUP, response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request
    ) {
        TokenResponseDto response = authService.login(request);
        return ResponseEntity.status(AuthSuccessCode.LOGIN.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.LOGIN, response));
    }

    @PostMapping("/oauth/kakao")
    public ResponseEntity<ApiResponse<TokenResponseDto>> loginWithKakao(
            @Valid @RequestBody KakaoLoginRequestDto request
    ) {
        TokenResponseDto response = authService.loginWithKakao(request);
        return ResponseEntity.status(AuthSuccessCode.SOCIAL_LOGIN.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.SOCIAL_LOGIN, response));
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<ApiResponse<TokenResponseDto>> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequestDto request
    ) {
        TokenResponseDto response = authService.loginWithGoogle(request);
        return ResponseEntity.status(AuthSuccessCode.SOCIAL_LOGIN.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.SOCIAL_LOGIN, response));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<TokenResponseDto>> reissue(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        TokenResponseDto response = authService.reissue(request);
        return ResponseEntity.status(AuthSuccessCode.REISSUE.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.REISSUE, response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Valid @RequestBody RefreshTokenRequestDto request
    ) {
        authService.logout(request);
        return ResponseEntity.status(AuthSuccessCode.LOGOUT.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.LOGOUT));
    }
}
