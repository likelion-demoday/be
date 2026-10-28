package com.example.resay.domain.analysis.model;

public final class CharacterInsightTextPolicy {

    public static final int NAME_MAX_LENGTH = 12;
    public static final int DESCRIPTION_MAX_LENGTH = 24;

    private CharacterInsightTextPolicy() {
    }

    public static String fitName(String value) {
        return abbreviate(value, NAME_MAX_LENGTH);
    }

    public static String fitDescription(String value) {
        return abbreviate(value, DESCRIPTION_MAX_LENGTH);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("캐릭터 문구는 비어 있을 수 없습니다.");
        }

        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }

        int contentLimit = maxLength - 1;
        int cutIndex = lastNaturalBoundary(normalized, contentLimit);
        return normalized.substring(0, cutIndex).stripTrailing() + "…";
    }

    private static int lastNaturalBoundary(String value, int contentLimit) {
        int minimumUsefulLength = contentLimit / 2;
        for (int index = contentLimit; index >= minimumUsefulLength; index--) {
            char previous = value.charAt(index - 1);
            if (Character.isWhitespace(previous) || isSentenceBoundary(previous)) {
                return index;
            }
        }
        return contentLimit;
    }

    private static boolean isSentenceBoundary(char value) {
        return value == '.' || value == '!' || value == '?' || value == '。';
    }
}
