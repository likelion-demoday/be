package com.example.resay.domain.recording.entity;

public enum RelationshipType {

    FRIEND_DAILY,
    COUPLE_DAILY,
    COUPLE_CONFLICT,
    PARENT_CHILD_CONFLICT;

    public String displayName() {
        return switch (this) {
            case FRIEND_DAILY -> "친구와의 대화";
            case COUPLE_DAILY, COUPLE_CONFLICT -> "연인과의 대화";
            case PARENT_CHILD_CONFLICT -> "가족과의 대화";
        };
    }
}
