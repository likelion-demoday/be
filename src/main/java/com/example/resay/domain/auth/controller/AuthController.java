package com.example.resay.domain.auth.controller;

import com.example.resay.domain.auth.code.AuthSuccessCode;
import com.example.resay.domain.auth.dto.EmailCheckRequestDto;
import com.example.resay.domain.auth.dto.EmailCheckResponseDto;
import com.example.resay.domain.auth.dto.GoogleLoginRequestDto;
import com.example.resay.domain.auth.dto.KakaoLoginRequestDto;
import com.example.resay.domain.auth.dto.LoginRequestDto;
import com.example.resay.domain.auth.dto.RefreshTokenRequestDto;
import com.example.resay.domain.auth.dto.SignupRequestDto;
import com.example.resay.domain.auth.dto.TokenResponseDto;
import com.example.resay.domain.auth.service.AuthService;
import com.example.resay.global.apiPayload.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
            @Valid @RequestBody SignupRequestDto request,
            HttpServletRequest httpRequest
    ) {
        TokenResponseDto response = authService.signup(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(AuthSuccessCode.SIGNUP.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.SIGNUP, response));
    }

    // 가입 화면의 이메일 입력 단계에서 중복 여부를 미리 알려주기 위한 API.
    // 이메일이 주소(URL)와 접근 로그에 남지 않도록 본문으로 받는다
    @PostMapping("/email/check")
    public ResponseEntity<ApiResponse<EmailCheckResponseDto>> checkEmail(
            @Valid @RequestBody EmailCheckRequestDto request,
            HttpServletRequest httpRequest
    ) {
        EmailCheckResponseDto response = authService.checkEmail(request, httpRequest.getRemoteAddr());
        return ResponseEntity.status(AuthSuccessCode.EMAIL_CHECK.getHttpStatus())
                .body(ApiResponse.onSuccess(AuthSuccessCode.EMAIL_CHECK, response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponseDto>> login(
            @Valid @RequestBody LoginRequestDto request,
            HttpServletRequest httpRequest
    ) {
        TokenResponseDto response = authService.login(request, httpRequest.getRemoteAddr());
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
