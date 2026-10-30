package com.example.resay.global.infrastructure.google;

/**
 * 검증을 통과한 구글 ID 토큰에서 꺼낸 사용자 정보.
 *
 * @param providerId 구글 계정 고유 ID(sub). 이메일은 바뀔 수 있어 식별자로 쓰지 않는다
 * @param email      구글이 검증한 이메일만 담는다. 검증되지 않았으면 null
 * @param name       구글 프로필 이름. 없으면 null
 */
public record GoogleUserInfo(
        String providerId,
        String email,
        String name
) {
}
