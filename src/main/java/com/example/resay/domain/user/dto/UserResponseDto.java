package com.example.resay.domain.user.dto;

import com.example.resay.domain.user.entity.Provider;
import com.example.resay.domain.user.entity.User;
import java.time.LocalDateTime;

public record UserResponseDto(
        Long id,
        // 이메일 제공에 동의하지 않은 소셜 계정은 null
        String email,
        String nickname,
        Provider provider,
        LocalDateTime createdAt,
        // 가입한 날이 1일째 (한국 시간 날짜 기준)
        long joinedDays,
        // 분석이 끝난 대화 중 사용자가 지우지 않은 것
        long analysisCount,
        // analysisCount에 센 녹음들의 길이 합 (초)
        long totalRecordingSeconds
) {

    public static UserResponseDto from(User user, long joinedDays, long analysisCount, long totalRecordingSeconds) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getCreatedAt(),
                joinedDays,
                analysisCount,
                totalRecordingSeconds
        );
    }
}
