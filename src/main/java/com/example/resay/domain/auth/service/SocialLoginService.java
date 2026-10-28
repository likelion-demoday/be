package com.example.resay.domain.auth.service;

import com.example.resay.domain.auth.service.SocialSignupTokenStore.PendingSocialSignup;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 가입 공통 흐름. 카카오·구글 등 제공자별 검증이 끝난 뒤, 사용자가 약관에 동의했을 때 계정을 만든다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private final UserRepository userRepository;

    // 같은 소셜 계정으로 받은 가입 토큰이 여러 개면(로그인을 두 번 함) 먼저 마친 쪽으로 가입되고,
    // 나머지는 새로 만들지 않고 그 계정을 돌려준다 (닉네임 · 동의 기록은 처음 가입한 그대로)
    @Transactional
    public User register(PendingSocialSignup pending, String nickname, boolean marketingAgreed) {
        return userRepository.findByProviderAndProviderId(pending.provider(), pending.providerId())
                .orElseGet(() -> {
                    User user = User.createSocial(
                            pending.provider(),
                            pending.providerId(),
                            resolveEmail(pending.email()),
                            nickname
                    );
                    user.agreeToTerms(LocalDateTime.now(), marketingAgreed);
                    return userRepository.saveAndFlush(user);
                });
    }

    /**
     * 이미 다른 계정이 쓰는 이메일이면 저장하지 않는다.
     * 이메일 하나에 계정이 여러 개 생기는 것을 막고, 자동으로 기존 계정에 연결하지도 않는다.
     */
    private String resolveEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return userRepository.existsByEmail(normalized) ? null : normalized;
    }
}
