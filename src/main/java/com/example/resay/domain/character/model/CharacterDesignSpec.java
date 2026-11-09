package com.example.resay.domain.character.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Set;
import java.util.regex.Pattern;

public record CharacterDesignSpec(
        @JsonProperty("character_name") String characterName,
        @JsonProperty("primary_conversation_pattern") String primaryConversationPattern,
        @JsonProperty("secondary_conversation_pattern") String secondaryConversationPattern,
        @JsonProperty("design_concept") String designConcept,
        @JsonProperty("creature_type") String creatureType,
        @JsonProperty("body_description") String bodyDescription,
        Outfit outfit,
        List<Prop> props,
        Palette palette,
        String pose,
        @JsonProperty("image_prompt") String imagePrompt
) {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private static final Pattern ASCII_WORD = Pattern.compile("[A-Za-z]{3,}");
    private static final List<String> FALLBACK_COLORS = List.of(
            "#2F6F73", "#7D3448", "#E8D9B5", "#E8A83E",
            "#405A7A", "#A65D57", "#8C9A6A", "#D4B483"
    );

    public CharacterDesignSpec {
        requireText(characterName, "characterName");
        requireText(primaryConversationPattern, "primaryConversationPattern");
        secondaryConversationPattern = normalizeNullable(secondaryConversationPattern);
        requireText(designConcept, "designConcept");
        requireText(creatureType, "creatureType");
        requireText(bodyDescription, "bodyDescription");
        if (outfit == null) {
            throw new IllegalArgumentException("outfit은 비어 있을 수 없습니다.");
        }
        props = props == null
                ? List.of()
                : props.stream().filter(item -> item != null).limit(4).toList();
        if (palette == null) {
            throw new IllegalArgumentException("palette는 비어 있을 수 없습니다.");
        }
        requireText(pose, "pose");
        imagePrompt = normalizeImagePrompt(
                imagePrompt,
                creatureType,
                bodyDescription,
                outfit,
                props,
                palette,
                pose
        );
        for (String hex : palette.hexColors()) {
            if (!imagePrompt.toUpperCase(Locale.ROOT).contains(hex.toUpperCase(Locale.ROOT))) {
                imagePrompt += " " + hex;
            }
        }
    }

    public record Outfit(
            String main,
            String secondary,
            List<String> materials,
            String headwear
    ) {

        public Outfit {
            requireText(main, "outfit.main");
            secondary = normalizeNullable(secondary);
            materials = materials == null
                    ? List.of()
                    : materials.stream()
                            .filter(item -> item != null && !item.isBlank())
                            .limit(6)
                            .toList();
            headwear = normalizeNullable(headwear);
        }
    }

    public record Prop(
            String name,
            String material,
            String placement
    ) {

        public Prop {
            requireText(name, "prop.name");
            requireText(material, "prop.material");
            requireText(placement, "prop.placement");
        }
    }

    public record Palette(
            Color body,
            @JsonProperty("outfit_a") Color outfitA,
            @JsonProperty("outfit_b") Color outfitB,
            Color accent
    ) {

        public Palette {
            if (body == null || outfitA == null || outfitB == null || accent == null) {
                throw new IllegalArgumentException("palette의 네 가지 색상은 모두 필요합니다.");
            }
            Set<String> distinctColors = new HashSet<>();
            body = normalizeColor(body, distinctColors);
            outfitA = normalizeColor(outfitA, distinctColors);
            outfitB = normalizeColor(outfitB, distinctColors);
            accent = normalizeColor(accent, distinctColors);
        }

        public List<String> hexColors() {
            return List.of(body.hex(), outfitA.hex(), outfitB.hex(), accent.hex());
        }

        private static Color normalizeColor(Color color, Set<String> usedColors) {
            String hex = color.hex();
            if (hex != null && HEX_COLOR.matcher(hex).matches()) {
                String normalized = hex.toUpperCase(Locale.ROOT);
                if (usedColors.add(normalized)) {
                    return new Color(color.name(), normalized);
                }
            }
            String replacement = FALLBACK_COLORS.stream()
                    .filter(candidate -> !usedColors.contains(candidate))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("대체 팔레트 색상을 선택할 수 없습니다."));
            usedColors.add(replacement);
            return new Color(color.name(), replacement);
        }
    }

    public record Color(String name, String hex) {

        public Color {
            requireText(name, "color.name");
            requireText(hex, "color.hex");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }

    private static String normalizeNullable(String value) {
        return value == null || value.isBlank() || "null".equalsIgnoreCase(value)
                ? null
                : value;
    }

    public CharacterDesignSpec withCharacterName(String correctedName) {
        return new CharacterDesignSpec(
                correctedName,
                primaryConversationPattern,
                secondaryConversationPattern,
                designConcept,
                creatureType,
                bodyDescription,
                outfit,
                props,
                palette,
                pose,
                imagePrompt
        );
    }

    private static String normalizeImagePrompt(
            String imagePrompt,
            String creatureType,
            String bodyDescription,
            Outfit outfit,
            List<Prop> props,
            Palette palette,
            String pose
    ) {
        if (imagePrompt != null && ASCII_WORD.matcher(imagePrompt).find()) {
            return imagePrompt;
        }
        List<String> parts = new ArrayList<>();
        parts.add("Create one cute premium 3D collectible character");
        parts.add(creatureType);
        parts.add(bodyDescription);
        parts.add("main outfit: " + outfit.main());
        if (outfit.secondary() != null) {
            parts.add("secondary outfit: " + outfit.secondary());
        }
        if (!outfit.materials().isEmpty()) {
            parts.add("materials: " + String.join(", ", outfit.materials()));
        }
        props.forEach(prop -> parts.add(
                "prop: " + prop.name() + ", " + prop.material() + ", " + prop.placement()
        ));
        parts.add("palette: " + String.join(", ", palette.hexColors()));
        parts.add(pose);
        parts.add("full body, soft studio lighting, transparent background");
        return String.join("; ", parts) + ".";
    }
}
