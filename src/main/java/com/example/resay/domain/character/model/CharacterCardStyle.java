package com.example.resay.domain.character.model;

import java.util.List;
import java.util.regex.Pattern;

public record CharacterCardStyle(
        String characterNameTextColor,
        List<String> cardBackgroundGradientColors
) {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-F]{6}$");

    public CharacterCardStyle {
        requireHexColor(characterNameTextColor, "characterNameTextColor");
        if (cardBackgroundGradientColors == null
                || cardBackgroundGradientColors.size() != 2) {
            throw new IllegalArgumentException("카드 배경 그라데이션 색상은 두 개여야 합니다.");
        }
        cardBackgroundGradientColors.forEach(color ->
                requireHexColor(color, "cardBackgroundGradientColors")
        );
        cardBackgroundGradientColors = List.copyOf(cardBackgroundGradientColors);
    }

    private static void requireHexColor(String value, String fieldName) {
        if (value == null || !HEX_COLOR.matcher(value).matches()) {
            throw new IllegalArgumentException(fieldName + "은(는) #RRGGBB 형식이어야 합니다.");
        }
    }
}
