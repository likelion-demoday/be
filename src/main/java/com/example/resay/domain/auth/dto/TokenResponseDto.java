package com.example.resay.domain.auth.dto;

import com.example.resay.domain.auth.service.RefreshTokenService.IssuedRefreshToken;
import com.example.resay.global.security.JwtTokenProvider.IssuedToken;

public record TokenResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        long refreshExpiresIn
) {

    private static final String BEARER = "Bearer";

    public static TokenResponseDto of(IssuedToken accessToken, IssuedRefreshToken refreshToken) {
        return new TokenResponseDto(
                accessToken.value(),
                BEARER,
                accessToken.expiresInSeconds(),
                refreshToken.value(),
                refreshToken.expiresInSeconds()
        );
    }
}
