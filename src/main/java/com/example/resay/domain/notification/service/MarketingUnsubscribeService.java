package com.example.resay.domain.notification.service;

import com.example.resay.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 광고 메일의 수신 거부 링크 처리. 마케팅 수신 동의를 철회한다 (마이페이지에서 끈 것과 같은 결과)
@Service
@RequiredArgsConstructor
public class MarketingUnsubscribeService {

    private final UnsubscribeTokenSigner unsubscribeTokenSigner;
    private final UserRepository userRepository;

    // 서명이 맞지 않으면 false. 이미 탈퇴했거나 거부한 사용자도 같은 결과(true)를 준다
    @Transactional
    public boolean unsubscribe(Long userId, String token) {
        if (!unsubscribeTokenSigner.verify(userId, token)) {
            return false;
        }
        userRepository.findById(userId)
                .ifPresent(user -> user.updateMarketingAgreement(false, LocalDateTime.now()));
        return true;
    }

    public boolean isValid(Long userId, String token) {
        return unsubscribeTokenSigner.verify(userId, token);
    }
}
