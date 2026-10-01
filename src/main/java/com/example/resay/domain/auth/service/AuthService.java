package com.example.resay.domain.auth.service;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.domain.auth.dto.GoogleLoginRequestDto;
import com.example.resay.domain.auth.dto.KakaoLoginRequestDto;
import com.example.resay.domain.auth.dto.LoginRequestDto;
import com.example.resay.domain.auth.dto.RefreshTokenRequestDto;
import com.example.resay.domain.auth.dto.SignupRequestDto;
import com.example.resay.domain.auth.dto.TokenResponseDto;
import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
import com.example.resay.global.infrastructure.google.GoogleIdTokenVerifier;
import com.example.resay.global.infrastructure.google.GoogleUserInfo;
import com.example.resay.global.infrastructure.kakao.KakaoClient;
import com.example.resay.global.infrastructure.kakao.KakaoProperties;
import com.example.resay.global.infrastructure.kakao.KakaoUserResponse;
import com.example.resay.global.security.JwtTokenProvider;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final SocialLoginService socialLoginService;
    private final KakaoClient kakaoClient;
    private final KakaoProperties kakaoProperties;
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    // 가입 직후 바로 서비스를 이용할 수 있도록 로그인과 같은 토큰을 발급한다
    @Transactional
    public TokenResponseDto signup(SignupRequestDto request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new GeneralException(AuthErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.createLocal(
                email,
                passwordEncoder.encode(request.password()),
                request.nickname()
        );
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // 중복 확인과 저장 사이에 같은 이메일로 동시 가입된 경우
            throw new GeneralException(AuthErrorCode.DUPLICATE_EMAIL);
        }
        return issueToken(user);
    }

    @Transactional
    public TokenResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> found.getProvider() == Provider.LOCAL)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_CREDENTIALS));
        return issueToken(user);
    }

    // 프론트가 카카오에서 받은 인가 코드로 로그인하거나 자동 가입한다
    @Transactional
    public TokenResponseDto loginWithKakao(KakaoLoginRequestDto request) {
        if (!kakaoProperties.isConfigured()) {
            throw new GeneralException(AuthErrorCode.SOCIAL_LOGIN_NOT_CONFIGURED);
        }
        if (!kakaoProperties.isAllowedRedirectUri(request.redirectUri())) {
            throw new GeneralException(AuthErrorCode.INVALID_REDIRECT_URI);
        }

        KakaoUserResponse kakaoUser = kakaoClient.fetchUser(request.code(), request.redirectUri());
        User user = socialLoginService.findOrCreate(
                Provider.KAKAO,
                String.valueOf(kakaoUser.id()),
                kakaoUser.email(),
                kakaoUser.nickname()
        );
        return issueToken(user);
    }

    // 프론트가 구글 로그인 버튼으로 받은 ID 토큰으로 로그인하거나 자동 가입한다
    @Transactional
    public TokenResponseDto loginWithGoogle(GoogleLoginRequestDto request) {
        GoogleUserInfo googleUser = googleIdTokenVerifier.verify(request.idToken());
        User user = socialLoginService.findOrCreate(
                Provider.GOOGLE,
                googleUser.providerId(),
                googleUser.email(),
                googleUser.name()
        );
        return issueToken(user);
    }

    // 사용한 리프레시 토큰은 폐기하고 새 토큰 쌍을 발급한다 (rotation)
    @Transactional
    public TokenResponseDto reissue(RefreshTokenRequestDto request) {
        Long userId = refreshTokenService.consume(request.refreshToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_REFRESH_TOKEN));
        return issueToken(user);
    }

    // 이미 폐기됐거나 없는 토큰이어도 결과는 같으므로 항상 성공으로 응답한다
    @Transactional
    public void logout(RefreshTokenRequestDto request) {
        refreshTokenService.revoke(request.refreshToken());
    }

    private TokenResponseDto issueToken(User user) {
        return TokenResponseDto.of(
                jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name()),
                refreshTokenService.issue(user.getId())
        );
    }

    // 대소문자만 다른 이메일로 중복 가입되지 않게 소문자로 통일한다 (앞뒤 공백은 요청 DTO에서 제거됨)
    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
