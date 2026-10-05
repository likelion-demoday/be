package com.example.resay.domain.transcription.model;

import java.util.List;

// 외부 전사 서비스의 결과를 도메인에서 쓰는 형태로 바꾼 것
public record RecognitionResult(
        boolean completed,
        String jobToken,
        List<RecognizedSegment> segments
) {

    public RecognitionResult {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }

    public record RecognizedSegment(
            String speakerLabel,
            long startMs,
            long endMs,
            String text
    ) {
    }
}
