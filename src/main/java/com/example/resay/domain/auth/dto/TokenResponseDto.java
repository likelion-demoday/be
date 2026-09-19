package com.example.resay.domain.auth.dto;

import com.example.resay.global.security.JwtTokenProvider.IssuedToken;

public record TokenResponseDto(
        String accessToken,
        String tokenType,
        long expiresIn
) {

    private static final String BEARER = "Bearer";

    public static TokenResponseDto from(IssuedToken accessToken) {
        return new TokenResponseDto(accessToken.value(), BEARER, accessToken.expiresInSeconds());
    }
}
