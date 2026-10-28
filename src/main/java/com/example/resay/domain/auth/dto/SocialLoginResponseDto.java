package com.example.resay.domain.auth.dto;

import com.example.resay.domain.auth.service.SocialSignupTokenStore.IssuedSignupToken;

/**
 * 기존 회원: isNewUser=false + 로그인 토큰 (TokenResponseDto와 같은 위치)
 * 신규 회원: isNewUser=true + registerToken. 아직 계정이 없으며 POST /api/v1/auth/oauth/signup 으로 가입을 마친다
 */
public record SocialLoginResponseDto(
        boolean isNewUser,
        String registerToken,
        Long registerExpiresIn,
        String accessToken,
        String tokenType,
        Long expiresIn,
        String refreshToken,
        Long refreshExpiresIn
) {

    public static SocialLoginResponseDto existingUser(TokenResponseDto token) {
        return new SocialLoginResponseDto(
                false,
                null,
                null,
                token.accessToken(),
                token.tokenType(),
                token.expiresIn(),
                token.refreshToken(),
                token.refreshExpiresIn()
        );
    }

    public static SocialLoginResponseDto newUser(IssuedSignupToken signupToken) {
        return new SocialLoginResponseDto(
                true,
                signupToken.value(),
                signupToken.expiresInSeconds(),
                null,
                null,
                null,
                null,
                null
        );
    }
}
