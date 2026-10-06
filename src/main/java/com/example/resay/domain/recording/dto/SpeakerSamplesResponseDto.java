package com.example.resay.domain.recording.dto;

import java.util.List;

public record SpeakerSamplesResponseDto(
        // <audio src>에 그대로 넣는 재생 주소 (10분 후 만료되면 다시 조회)
        String audioUrl,
        List<SpeakerSample> speakers
) {

    public record SpeakerSample(
            String speakerLabel,
            // 이 화자의 목소리를 들려줄 구간 (긴 발화 순으로 고른 뒤 시간순 정렬)
            List<SampleRange> samples
    ) {
    }

    public record SampleRange(
            long startMs,
            long endMs
    ) {
    }
}
