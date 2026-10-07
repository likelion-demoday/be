package com.example.resay.domain.character.event;

public record CharacterImageRequestedEvent(
        Long analysisId,
        Long recordingId
) {

    public CharacterImageRequestedEvent {
        if (analysisId == null || analysisId <= 0) {
            throw new IllegalArgumentException("analysisId는 양수여야 합니다.");
        }
        if (recordingId == null || recordingId <= 0) {
            throw new IllegalArgumentException("recordingId는 양수여야 합니다.");
        }
    }
}
