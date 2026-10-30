package com.example.resay.domain.auth.dto;

public record EmailCheckResponseDto(
        // 이 이메일로 가입할 수 있는지 (이미 가입된 이메일이면 false)
        boolean available
) {
}
