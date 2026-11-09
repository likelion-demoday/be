package com.example.resay.domain.character.model;

import com.example.resay.domain.analysis.model.AnalysisScenario;
import com.example.resay.domain.analysis.model.SpeakerRole;
import java.util.List;

public record CharacterImageGenerationCommand(
        AnalysisScenario scenario,
        SpeakerRole speakerRole,
        String characterName,
        String characterSummary,
        String mainTrait,
        String secondaryTrait,
        String selectionEvidence,
        List<String> conversationKeywords,
        CharacterCardStyle cardStyle
) {

    private static final int NAME_MAX_LENGTH = 50;
    private static final int TEXT_MAX_LENGTH = 500;
    private static final int KEYWORD_MAX_LENGTH = 80;
    private static final int KEYWORD_MAX_COUNT = 5;

    public CharacterImageGenerationCommand {
        if (scenario == null) {
            throw new IllegalArgumentException("scenario는 비어 있을 수 없습니다.");
        }
        if (speakerRole == null || !scenario.requiredRoles().contains(speakerRole)) {
            throw new IllegalArgumentException("시나리오에 맞는 speakerRole이 필요합니다.");
        }
        requireText(characterName, "characterName");
        requireText(characterSummary, "characterSummary");
        requireText(mainTrait, "mainTrait");
        requireText(selectionEvidence, "selectionEvidence");
        conversationKeywords = conversationKeywords == null
                ? List.of()
                : conversationKeywords.stream()
                        .filter(CharacterImageGenerationCommand::hasText)
                        .map(String::trim)
                        .map(CharacterImageGenerationCommand::boundedKeyword)
                        .distinct()
                        .limit(KEYWORD_MAX_COUNT)
                        .toList();
        if (cardStyle == null) {
            throw new IllegalArgumentException("cardStyle은 비어 있을 수 없습니다.");
        }
        secondaryTrait = hasText(secondaryTrait) ? secondaryTrait : "없음";
        requireMaxLength(characterName, "characterName", NAME_MAX_LENGTH);
        requireMaxLength(characterSummary, "characterSummary", TEXT_MAX_LENGTH);
        requireMaxLength(mainTrait, "mainTrait", TEXT_MAX_LENGTH);
        requireMaxLength(secondaryTrait, "secondaryTrait", TEXT_MAX_LENGTH);
        requireMaxLength(selectionEvidence, "selectionEvidence", TEXT_MAX_LENGTH);
    }

    public CharacterImageGenerationCommand(
            AnalysisScenario scenario,
            SpeakerRole speakerRole,
            String characterName,
            String characterSummary,
            String mainTrait,
            String secondaryTrait,
            String selectionEvidence,
            CharacterCardStyle cardStyle
    ) {
        this(
                scenario,
                speakerRole,
                characterName,
                characterSummary,
                mainTrait,
                secondaryTrait,
                selectionEvidence,
                List.of(),
                cardStyle
        );
    }

    private static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + "은(는) 비어 있을 수 없습니다.");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void requireMaxLength(String value, String fieldName, int maxLength) {
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "의 길이가 너무 깁니다.");
        }
    }

    private static String boundedKeyword(String keyword) {
        if (keyword.length() <= KEYWORD_MAX_LENGTH) {
            return keyword;
        }
        return keyword.substring(0, KEYWORD_MAX_LENGTH);
    }
}
