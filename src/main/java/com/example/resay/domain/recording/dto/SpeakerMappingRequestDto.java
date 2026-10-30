package com.example.resay.domain.recording.dto;

import com.example.resay.domain.recording.entity.ParentChildRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SpeakerMappingRequestDto(
        // 사용자가 "본인 목소리"로 고른 화자 (예: SPEAKER_1)
        @NotBlank(message = "본인 화자를 선택해 주세요.")
        @Pattern(regexp = "SPEAKER_\\d+", message = "화자 형식이 올바르지 않습니다.")
        String selfSpeakerLabel,

        @NotBlank(message = "상대방 닉네임을 입력해 주세요.")
        @Size(max = 20, message = "닉네임은 20자 이하여야 합니다.")
        String partnerNickname,

        // 부모-자녀 대화일 때만 보낸다 (PARENT / CHILD)
        ParentChildRole selfRole
) {
}
