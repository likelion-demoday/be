package com.example.resay.domain.auth.service;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import com.example.resay.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 소셜 로그인 공통 흐름. 카카오·구글 등 제공자별 검증이 끝난 뒤의 처리를 담당한다.
 */
@Service
@RequiredArgsConstructor
public class SocialLoginService {

    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;

    @Transactional
    public User findOrCreate(Provider provider, String providerId, String email, String nickname) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> userRepository.saveAndFlush(User.createSocial(
                        provider,
                        providerId,
                        resolveEmail(email),
                        resolveNickname(nickname, providerId)
                )));
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

    // 닉네임 제공에 동의하지 않은 계정도 가입할 수 있어야 한다
    private String resolveNickname(String nickname, String providerId) {
        if (nickname == null || nickname.isBlank()) {
            return "사용자" + providerId.substring(Math.max(0, providerId.length() - 4));
        }
        String trimmed = nickname.trim();
        return trimmed.length() > NICKNAME_MAX_LENGTH
                ? trimmed.substring(0, NICKNAME_MAX_LENGTH)
                : trimmed;
    }
}
