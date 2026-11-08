package com.example.resay.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// body는 일반 텍스트다. 줄바꿈은 그대로 보이고 HTML 태그는 글자로 보인다
public record ServiceAnnouncementRequestDto(
        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 100, message = "제목은 100자 이하여야 합니다.")
        // 메일 제목에는 줄바꿈이 들어갈 수 없다 (메일 서비스가 거절하거나 제목이 깨진다)
        @Pattern(regexp = "[^\\r\\n]*", message = "제목에 줄바꿈을 넣을 수 없습니다.")
        String subject,
        @NotBlank(message = "본문을 입력해 주세요.")
        @Size(max = 5000, message = "본문은 5000자 이하여야 합니다.")
        String body
) {

    public ServiceAnnouncementRequestDto {
        subject = subject == null ? null : subject.strip();
        body = body == null ? null : body.strip();
    }
}
