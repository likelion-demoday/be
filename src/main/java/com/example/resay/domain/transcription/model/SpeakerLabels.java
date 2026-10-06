package com.example.resay.domain.transcription.model;

// API·분석 입력에서 쓰는 화자 표기(SPEAKER_1)와 저장된 전사 서비스 화자 번호(1) 사이의 변환
public final class SpeakerLabels {

    private static final String PREFIX = "SPEAKER_";

    private SpeakerLabels() {
    }

    public static String toExternal(String storedLabel) {
        return PREFIX + storedLabel;
    }

    public static String toStored(String externalLabel) {
        if (externalLabel == null || !externalLabel.startsWith(PREFIX)) {
            return null;
        }
        String stored = externalLabel.substring(PREFIX.length());
        return stored.isBlank() ? null : stored;
    }
}
