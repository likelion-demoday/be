package com.example.resay.domain.auth.service;

import com.example.resay.domain.auth.code.AuthErrorCode;
import com.example.resay.domain.auth.dto.LoginRequestDto;
import com.example.resay.domain.auth.dto.SignupRequestDto;
import com.example.resay.domain.auth.dto.TokenResponseDto;
import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import com.example.resay.global.exception.GeneralException;
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

    public TokenResponseDto login(LoginRequestDto request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .filter(found -> found.getProvider() == Provider.LOCAL)
                .filter(found -> passwordEncoder.matches(request.password(), found.getPassword()))
                .orElseThrow(() -> new GeneralException(AuthErrorCode.INVALID_CREDENTIALS));
        return issueToken(user);
    }

    private TokenResponseDto issueToken(User user) {
        return TokenResponseDto.from(
                jwtTokenProvider.issueAccessToken(user.getId(), user.getRole().name())
        );
    }

    // 대소문자만 다른 이메일로 중복 가입되지 않게 소문자로 통일한다 (앞뒤 공백은 요청 DTO에서 제거됨)
    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
