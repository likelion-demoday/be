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
        LocalDateTime createdAt
) {

    public static UserResponseDto from(User user) {
        return new UserResponseDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProvider(),
                user.getCreatedAt()
        );
    }
}
